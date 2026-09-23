package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class DischargeReminderService {
  private final JdbcTemplate jdbc;
  private final ConfirmationEmailService email;
  private final Actor actor;
  private final List<Integer> windows;
  private final long retryDelayMillis;
  private final long sendingLeaseMillis;
  private final int outcomesLimit;
  private final int maxAttempts;

  public DischargeReminderService(
      JdbcTemplate jdbc,
      ConfirmationEmailService email,
      Actor actor,
      @Value("${app.discharge-reminders.windows:7,3,1}") String configuredWindows,
      @Value("${app.discharge-reminders.retry-delay-ms:3600000}") long retryDelayMillis,
      @Value("${app.discharge-reminders.sending-lease-ms:900000}") long sendingLeaseMillis,
      @Value("${app.discharge-reminders.outcomes-limit:50}") int outcomesLimit,
      @Value("${app.discharge-reminders.max-attempts:5}") int maxAttempts) {
    this.jdbc = jdbc;
    this.email = email;
    this.actor = actor;
    this.windows = parseWindows(configuredWindows);
    if (retryDelayMillis < 1 || sendingLeaseMillis < 1 || outcomesLimit < 1 || outcomesLimit > 500
        || maxAttempts < 1 || maxAttempts > 20)
      throw new IllegalArgumentException("Invalid discharge reminder retry, lease, attempt, or outcome limit configuration.");
    this.retryDelayMillis = retryDelayMillis;
    this.sendingLeaseMillis = sendingLeaseMillis;
    this.outcomesLimit = outcomesLimit;
    this.maxAttempts = maxAttempts;
  }

  static List<Integer> parseWindows(String configured) {
    if (configured == null || configured.isBlank())
      throw new IllegalArgumentException("Configure at least one discharge reminder window.");
    var parsed = new LinkedHashSet<Integer>();
    try {
      for (var value : configured.split(",")) {
        int days = Integer.parseInt(value.trim());
        if (days < 1 || days > 365 || !parsed.add(days))
          throw new IllegalArgumentException("Discharge reminder windows must be unique day counts from 1 to 365.");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Discharge reminder windows must be comma-separated day counts.", e);
    }
    if (parsed.isEmpty()) throw new IllegalArgumentException("Configure at least one discharge reminder window.");
    return parsed.stream().sorted().toList();
  }

  public int processDueReminders() {
    return processDueReminders(LocalDate.now(ZoneOffset.UTC));
  }

  public int processDueReminders(LocalDate today) {
    cancelStaleReminders();
    int attempted = 0;
    for (int daysBefore : windows) {
      var expectedDate = today.plusDays(daysBefore);
      var admissions = jdbc.query("""
          select id, department_id, attending_doctor_id, expected_discharge_date
            from admissions
           where status = 'ACTIVE' and expected_discharge_date = ?
           order by department_id, id
          """, DUE_ADMISSION, expectedDate);
      for (var admission : admissions) {
        if (process(admission, expectedDate, daysBefore)) attempted++;
      }
    }
    return attempted;
  }

  public List<DeliveryOutcome> recentOutcomes() {
    var currentActor = actor.user();
    if (!List.of("ADMIN", "MEDICAL_STAFF", "DOCTOR").contains(currentActor.getRole())
        || DepartmentContext.id() <= 0
        || (currentActor.getRole().equals("DOCTOR") && currentActor.getDoctorId() == null))
      throw new AccessDeniedException("Department staff or doctor access is required.");
    long departmentId = DepartmentContext.id();
    Long doctorId = actor.doctor() ? actor.user().getDoctorId() : null;
    String sql = """
        select r.id, r.expected_discharge_date, r.window_days, r.status,
               r.recipient_count, r.attempt_count,
               r.last_attempt_at, r.provider_message_id, r.error_code, r.created_at
          from discharge_reminders r
          join admissions a on a.id = r.admission_id and a.department_id = r.department_id
         where r.department_id = ?
        """;
    var parameters = new ArrayList<Object>();
    parameters.add(departmentId);
    if (doctorId != null) {
      sql += " and a.attending_doctor_id = ?";
      parameters.add(doctorId);
    }
    sql += " order by r.created_at desc, r.id desc limit ?";
    parameters.add(outcomesLimit);
    return jdbc.query(sql, DELIVERY_OUTCOME, parameters.toArray());
  }

  private boolean process(DueAdmission admission, LocalDate expectedDate, int daysBefore) {
    jdbc.update("""
        insert into discharge_reminders(
            department_id, admission_id, expected_discharge_date, window_days, status)
        values (?, ?, ?, ?, 'PENDING')
        on conflict (admission_id, expected_discharge_date, window_days) do nothing
        """, admission.departmentId(), admission.id(), expectedDate, daysBefore);
    Long reminderId = jdbc.queryForObject("""
        select id from discharge_reminders
         where admission_id = ? and expected_discharge_date = ? and window_days = ?
        """, Long.class, admission.id(), expectedDate, daysBefore);
    var recipients = recipients(admission.departmentId(), admission.doctorId());
    if (recipients.isEmpty()) {
      jdbc.update("""
          update discharge_reminders
             set status = 'NO_RECIPIENT', recipient_count = 0,
                 error_code = 'NO_VERIFIED_RECIPIENT', updated_at = now()
         where id = ? and status in ('PENDING', 'FAILED', 'NO_RECIPIENT')
          """, reminderId);
      return false;
    }
    jdbc.update("""
        update discharge_reminders
           set status = 'PENDING', error_code = null, updated_at = now()
         where id = ? and status = 'NO_RECIPIENT'
        """, reminderId);
    var now = Instant.now();
    int claimed = jdbc.update("""
        update discharge_reminders r
           set status = 'SENDING', recipient_count = ?, attempt_count = attempt_count + 1,
               last_attempt_at = ?, updated_at = ?
         where r.id = ?
           and (r.status = 'PENDING'
                or (r.status = 'FAILED' and r.attempt_count < ? and r.next_attempt_at <= ?)
                or (r.status = 'SENDING' and r.last_attempt_at <= ?))
           and exists (
               select 1 from admissions a
                where a.id = r.admission_id and a.department_id = r.department_id
                  and a.status = 'ACTIVE' and a.expected_discharge_date = r.expected_discharge_date)
        """, recipients.size(), Timestamp.from(now), Timestamp.from(now), reminderId,
        maxAttempts, Timestamp.from(now), Timestamp.from(now.minusMillis(sendingLeaseMillis)));
    if (claimed == 0) return false;
    if (!isCurrent(admission.id(), admission.departmentId(), expectedDate)) {
      markCancelled(reminderId);
      return false;
    }
    String idempotencyKey = "discharge-" + admission.id() + "-" + expectedDate + "-" + daysBefore;
    String subject = "Expected discharge in " + daysBefore + (daysBefore == 1 ? " day" : " days");
    String message = "An admission assigned to your department is scheduled for " + expectedDate + ".\n\n"
        + "This date is an operational estimate and does not confirm that discharge has occurred. "
        + "Sign in to your department workspace to review the current plan.";
    try {
      var delivery = email.sendReminder(recipients, subject, message, idempotencyKey);
      jdbc.update("""
          update discharge_reminders
             set status = 'ACCEPTED', provider_message_id = ?, error_code = null,
                 next_attempt_at = ?, updated_at = now()
           where id = ? and status = 'SENDING'
          """, delivery.providerMessageId(), Timestamp.from(Instant.now()), reminderId);
    } catch (RuntimeException e) {
      String errorCode = e instanceof ApiException api ? api.code : "EMAIL_UNAVAILABLE";
      jdbc.update("""
          update discharge_reminders
             set status = 'FAILED', error_code = ?, next_attempt_at = ?, updated_at = now()
           where id = ? and status = 'SENDING'
          """, errorCode, Timestamp.from(Instant.now().plusMillis(retryDelayMillis)), reminderId);
    }
    return true;
  }

  private List<String> recipients(long departmentId, long doctorId) {
    return jdbc.queryForList("""
        select distinct u.email
          from department_memberships m
          join app_users u on u.id = m.user_id
         where m.department_id = ? and u.enabled = true and u.email_verified = true
           and u.email is not null and trim(u.email) <> ''
           and (m.role in ('ADMIN', 'MEDICAL_STAFF')
                or (m.role = 'DOCTOR' and m.doctor_id = ?))
         order by u.email
        """, String.class, departmentId, doctorId);
  }

  private boolean isCurrent(long admissionId, long departmentId, LocalDate expectedDate) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from admissions
                       where id = ? and department_id = ? and status = 'ACTIVE'
                         and expected_discharge_date = ?)
        """, Boolean.class, admissionId, departmentId, expectedDate));
  }

  private void cancelStaleReminders() {
    jdbc.update("""
        update discharge_reminders r
           set status = 'CANCELLED', error_code = 'ADMISSION_CHANGED', updated_at = now()
         where r.status in ('PENDING', 'FAILED', 'NO_RECIPIENT')
           and not exists (
               select 1 from admissions a
                where a.id = r.admission_id and a.department_id = r.department_id
                  and a.status = 'ACTIVE' and a.expected_discharge_date = r.expected_discharge_date)
        """);
    jdbc.update("""
        update discharge_reminders r
           set status = 'CANCELLED', error_code = 'ADMISSION_CHANGED', updated_at = now()
         where r.status = 'SENDING' and r.last_attempt_at <= ?
           and not exists (
               select 1 from admissions a
                where a.id = r.admission_id and a.department_id = r.department_id
                  and a.status = 'ACTIVE' and a.expected_discharge_date = r.expected_discharge_date)
        """, Timestamp.from(Instant.now().minusMillis(sendingLeaseMillis)));
  }

  private void markCancelled(long reminderId) {
    jdbc.update("""
        update discharge_reminders
           set status = 'CANCELLED', error_code = 'ADMISSION_CHANGED', updated_at = now()
         where id = ? and status = 'SENDING'
        """, reminderId);
  }

  public record DeliveryOutcome(
      long id,
      LocalDate expectedDischargeDate,
      int windowDays,
      String status,
      int recipientCount,
      int attemptCount,
      Instant lastAttemptAt,
      String providerMessageId,
      String errorCode,
      Instant createdAt) {}

  private record DueAdmission(long id, long departmentId, long doctorId) {}

  private static final RowMapper<DueAdmission> DUE_ADMISSION = (rs, row) -> new DueAdmission(
      rs.getLong("id"), rs.getLong("department_id"), rs.getLong("attending_doctor_id"));

  private static final RowMapper<DeliveryOutcome> DELIVERY_OUTCOME = (rs, row) -> new DeliveryOutcome(
      rs.getLong("id"), rs.getObject("expected_discharge_date", LocalDate.class), rs.getInt("window_days"),
      rs.getString("status"), rs.getInt("recipient_count"), rs.getInt("attempt_count"),
      instant(rs, "last_attempt_at"), rs.getString("provider_message_id"), rs.getString("error_code"),
      instant(rs, "created_at"));

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
