package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.service.ConfirmationEmailService;
import com.example.hospital.service.DischargeReminderService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.seed=false",
    "app.bootstrap-password=IntegrationPassword123!",
    "app.discharge-reminders.enabled=false",
    "app.discharge-reminders.windows=7,3,1",
    "app.discharge-reminders.retry-delay-ms=3600000",
    "app.discharge-reminders.sending-lease-ms=1",
    "app.discharge-reminders.max-attempts=2",
    "server.servlet.session.cookie.secure=false"
})
@AutoConfigureMockMvc
class DischargeReminderIntegrationTest extends HospitalSupport {
  private static final String SCHEMA =
      "issue_149_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    HospitalSupport.database(registry);
    registry.add("spring.flyway.default-schema", () -> SCHEMA);
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired DischargeReminderService reminders;
  @MockitoBean ConfirmationEmailService email;

  private String runKey;
  private long doctorOne;
  private long doctorTwo;
  @BeforeEach
  void setUp() {
    runKey = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    doctorOne = createDoctor("one");
    doctorTwo = createDoctor("two");
    createUser("doctor", "DOCTOR", doctorOne, null, false, true, 1);
    reset(email);
    when(email.sendReminder(anyList(), anyString(), anyString(), anyString()))
        .thenReturn(new ConfirmationEmailService.DeliveryResult("provider-message-id"));
  }

  @AfterEach
  void cleanUp() {
    if (runKey == null) return;
    jdbc.update("delete from admissions where admission_number like ?", "REMINDER-" + runKey + "-%");
    jdbc.update("delete from app_users where username like ?", "reminder_" + runKey + "_%");
    jdbc.update("delete from app_users where username in ('doctor', 'patient')");
    jdbc.update("delete from patients where patient_identifier like ?", "REMINDER-" + runKey + "-%");
    jdbc.update("delete from doctors where doctor_identifier like ?", "REMINDER-" + runKey + "-%");
  }

  @Test
  void dispatchesOnceToTheVerifiedAssignedTeamAndScopesOutcomes() throws Exception {
    createRecipients();
    var today = LocalDate.now(ZoneOffset.UTC);
    var first = admission("first", doctorOne, today.plusDays(1));
    var second = admission("second", doctorTwo, today.plusDays(1));
    admission("outside-window", doctorOne, today.plusDays(2));
    long firstPatientId = jdbc.queryForObject(
        "select patient_id from admissions where id=?", Long.class, first);
    createPatientAccount(firstPatientId);

    assertThat(reminders.processDueReminders(today)).isEqualTo(2);
    assertThat(reminders.processDueReminders(today)).isZero();

    var recipients = ArgumentCaptor.forClass(List.class);
    var messages = ArgumentCaptor.forClass(String.class);
    verify(email, times(2)).sendReminder(recipients.capture(), anyString(), messages.capture(), anyString());
    assertThat(recipients.getAllValues())
        .containsExactlyInAnyOrder(
            List.of(
                address("admin"), address("doctor1"), address("staff")),
            List.of(
                address("admin"), address("doctor2"), address("staff")));
    assertThat(messages.getAllValues())
        .allSatisfy(message -> assertThat(message)
            .contains(today.plusDays(1).toString())
            .doesNotContain("REMINDER-", "PATIENT-"));
    assertThat(jdbc.queryForList(
        "select status from discharge_reminders order by id", String.class))
        .containsExactly("ACCEPTED", "ACCEPTED");
    assertThat(jdbc.queryForObject(
        "select count(*) from discharge_reminders where attempt_count=1 and recipient_count=3",
        Integer.class)).isEqualTo(2);

    mvc.perform(get("/api/v1/reports/discharge-reminders").with(user("doctor")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
        .andExpect(jsonPath("$[0].expectedDischargeDate").value(today.plusDays(1).toString()))
        .andExpect(jsonPath("$[0].admissionId").doesNotExist())
        .andExpect(jsonPath("$[0].admissionNumber").doesNotExist())
        .andExpect(jsonPath("$[0].email").doesNotExist());
    mvc.perform(get("/api/v1/reports/discharge-reminders").with(user("admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    mvc.perform(get("/api/v1/reports/discharge-reminders").with(user("patient")))
        .andExpect(status().isForbidden());

    assertThat(first).isPositive();
    assertThat(second).isPositive();
  }

  @Test
  void recordsMissingRecipientsThenSendsWhenAVerifiedTeamAddressAppears() {
    var today = LocalDate.now(ZoneOffset.UTC);
    admission("late-recipient", doctorOne, today.plusDays(1));

    assertThat(reminders.processDueReminders(today)).isZero();
    assertThat(jdbc.queryForObject(
        "select status from discharge_reminders", String.class)).isEqualTo("NO_RECIPIENT");
    assertThat(jdbc.queryForObject(
        "select error_code from discharge_reminders", String.class)).isEqualTo("NO_VERIFIED_RECIPIENT");

    createRecipients();
    assertThat(reminders.processDueReminders(today)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "select status from discharge_reminders", String.class)).isEqualTo("ACCEPTED");
    assertThat(jdbc.queryForObject(
        "select attempt_count from discharge_reminders", Integer.class)).isEqualTo(1);
  }

  @Test
  void retriesProviderFailuresOnlyUntilTheConfiguredAttemptLimit() {
    createRecipients();
    var today = LocalDate.now(ZoneOffset.UTC);
    long admissionId = admission("retry-limit", doctorOne, today.plusDays(1));
    var calls = new AtomicInteger();
    doAnswer(invocation -> {
      if (calls.incrementAndGet() <= 2) {
        throw new com.example.hospital.api.ApiException(
            503, "EMAIL_DELIVERY_FAILED", "Provider temporarily unavailable.");
      }
      return new ConfirmationEmailService.DeliveryResult("provider-message-id");
    }).when(email).sendReminder(anyList(), anyString(), anyString(), anyString());

    assertThat(reminders.processDueReminders(today)).isEqualTo(1);
    assertThat(reminders.processDueReminders(today)).isZero();
    makeRetryDue(admissionId);
    assertThat(reminders.processDueReminders(today)).isEqualTo(1);
    makeRetryDue(admissionId);
    assertThat(reminders.processDueReminders(today)).isZero();

    assertThat(calls).hasValue(2);
    assertThat(jdbc.queryForObject("select status from discharge_reminders", String.class))
        .isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("select attempt_count from discharge_reminders", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("select error_code from discharge_reminders", String.class))
        .isEqualTo("EMAIL_DELIVERY_FAILED");
  }

  @Test
  void cancelsAnExpiredSendWhenItsAdmissionDateChanges() {
    var today = LocalDate.now(ZoneOffset.UTC);
    long admissionId = admission("stale-send", doctorOne, today.plusDays(1));
    jdbc.update("""
        insert into discharge_reminders(
          department_id, admission_id, expected_discharge_date, window_days,
          status, attempt_count, last_attempt_at)
        values (1, ?, ?, 1, 'SENDING', 1, now()-interval '1 minute')
        """, admissionId, Date.valueOf(today.plusDays(1)));
    jdbc.update(
        "update admissions set expected_discharge_date=? where id=?",
        Date.valueOf(today.plusDays(5)), admissionId);

    assertThat(reminders.processDueReminders(today)).isZero();
    assertThat(jdbc.queryForObject("select status from discharge_reminders", String.class))
        .isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("select error_code from discharge_reminders", String.class))
        .isEqualTo("ADMISSION_CHANGED");
  }

  private void createRecipients() {
    createUser("admin", "ADMIN", null, "admin", true, true, 1);
    createUser("staff", "MEDICAL_STAFF", null, "staff", true, true, 1);
    createUser("doctor1", "DOCTOR", doctorOne, "doctor1", true, true, 1);
    createUser("doctor2", "DOCTOR", doctorTwo, "doctor2", true, true, 1);
    createUser("unverified", "MEDICAL_STAFF", null, "unverified", false, true, 1);
    createUser("disabled", "MEDICAL_STAFF", null, "disabled", true, false, 1);
  }

  private void makeRetryDue(long admissionId) {
    assertThat(jdbc.update("update discharge_reminders set next_attempt_at=? where admission_id=?",
        Timestamp.from(Instant.now().minusSeconds(60)), admissionId)).isEqualTo(1);
  }

  private void createUser(
      String name, String role, Long doctorId, String emailSuffix,
      boolean verified, boolean enabled, long departmentId) {
    String username = "doctor".equals(name) ? name : "reminder_" + runKey + "_" + name;
    String address = emailSuffix == null ? null : address(emailSuffix);
    jdbc.update("""
        insert into app_users(username, password_hash, role, enabled, doctor_id, email, email_verified)
        values (?, 'test-only-password-hash', ?, ?, ?, ?, ?)
        """, username, role, enabled, doctorId, address, verified);
    if ("PATIENT".equals(role)) return;
    Long userId = jdbc.queryForObject(
        "select id from app_users where username=?", Long.class, username);
    jdbc.update("""
        insert into department_memberships(department_id, user_id, role, doctor_id)
        values (?, ?, ?, ?)
        """, departmentId, userId, role, doctorId);
  }

  private void createPatientAccount(long patientId) {
    jdbc.update("""
        insert into app_users(username, password_hash, role, enabled, patient_id)
        values ('patient', 'test-only-password-hash', 'PATIENT', true, ?)
        """, patientId);
  }

  private long createDoctor(String name) {
    return jdbc.queryForObject("""
        insert into doctors(department_id, doctor_identifier, first_name, last_name, specialty, active)
        values (1, ?, ?, 'Test', 'General medicine', true)
        returning id
        """, Long.class, "REMINDER-" + runKey + "-" + name, "Doctor " + name);
  }

  private long admission(String name, long doctorId, LocalDate expectedDate) {
    String key = "REMINDER-" + runKey + "-" + name;
    long patientId = jdbc.queryForObject("""
        insert into patients(department_id, patient_identifier, first_name, last_name, date_of_birth)
        values (1, ?, 'Reminder', 'Test', date '1980-01-01')
        returning id
        """, Long.class, key);
    Long adminId = jdbc.queryForObject(
        "select id from app_users where username='admin'", Long.class);
    return jdbc.queryForObject("""
        insert into admissions(
          department_id, admission_number, patient_id, attending_doctor_id,
          admission_date_time, status, created_by, expected_discharge_date)
        values (1, ?, ?, ?, now(), 'ACTIVE', ?, ?)
        returning id
        """, Long.class, key, patientId, doctorId, adminId, Date.valueOf(expectedDate));
  }

  private String address(String name) {
    return "reminder-" + runKey + "-" + name + "@example.test";
  }
}
