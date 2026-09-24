package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.PushSubscriptionInput;
import com.example.hospital.api.TaskReminderPreferencesInput;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Privacy-safe task reminder scheduling and browser push subscription management. */
@Service
public class TaskReminderService {
  private static final Logger log = LoggerFactory.getLogger(TaskReminderService.class);
  private static final Set<Integer> LEAD_MINUTES = Set.of(0, 5, 10, 15, 30, 60);
  private static final int MAX_ATTEMPTS = 5;
  private static final long RETRY_DELAY_MS = 60_000L;
  private static final long SENDING_LEASE_MS = 10 * 60_000L;
  private static final String GENERIC_TITLE = "Care task reminder";
  private static final String GENERIC_BODY = "You have a care task to review. Sign in to view it.";

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final ObjectMapper json;
  private final String vapidPublicKey;
  private final String vapidPrivateKey;
  private final String vapidSubject;

  public TaskReminderService(
      JdbcTemplate jdbc,
      Actor actor,
      ObjectMapper json,
      @Value("${app.push.vapid-public-key:}") String vapidPublicKey,
      @Value("${app.push.vapid-private-key:}") String vapidPrivateKey,
      @Value("${app.push.vapid-subject:mailto:admin@example.invalid}") String vapidSubject) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.json = json;
    this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.strip();
    this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.strip();
    this.vapidSubject = vapidSubject;
  }

  public Preferences preferences() {
    var user = requireStaff();
    return loadPreferences(user.getId());
  }

  @Transactional
  public Preferences updatePreferences(TaskReminderPreferencesInput input) {
    var user = requireStaff();
    if (input.minutesBefore() == null || !LEAD_MINUTES.contains(input.minutesBefore()))
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a reminder time from the available options.");
    if ((input.quietHoursStart() == null) != (input.quietHoursEnd() == null))
      throw new ApiException(400, "VALIDATION_ERROR", "Set both quiet-hours times or leave both empty.");
    ZoneId zone;
    try {
      zone = ZoneId.of(input.timeZone() == null || input.timeZone().isBlank() ? "UTC" : input.timeZone());
    } catch (RuntimeException ex) {
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a valid time zone.");
    }
    jdbc.update("""
        insert into task_reminder_preferences(user_id, opted_in, time_zone, minutes_before,
          quiet_hours_start, quiet_hours_end, operational_alerts, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, now())
        on conflict (user_id) do update set opted_in = excluded.opted_in,
          time_zone = excluded.time_zone, minutes_before = excluded.minutes_before,
          quiet_hours_start = excluded.quiet_hours_start, quiet_hours_end = excluded.quiet_hours_end,
          operational_alerts = excluded.operational_alerts, updated_at = now()
        """, user.getId(), input.optedIn(), zone.getId(), input.minutesBefore(),
        input.quietHoursStart(), input.quietHoursEnd(), input.operationalAlerts());
    if (!input.optedIn()) {
      jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'OPTED_OUT', updated_at = now()"
          + " where recipient_user_id = ? and status in ('PENDING', 'FAILED')", user.getId());
    } else {
      reconcileForUser(user.getId());
    }
    return loadPreferences(user.getId());
  }

  public String vapidPublicKey() {
    requireStaff();
    return pushAvailable() ? vapidPublicKey : null;
  }

  @Transactional
  public SubscriptionStatus subscribe(PushSubscriptionInput input) {
    var user = requireStaff();
    if (!pushAvailable())
      throw new ApiException(503, "PUSH_UNAVAILABLE", "Browser push is not configured for this service.");
    URI endpoint;
    try {
      endpoint = URI.create(input.endpoint());
    } catch (RuntimeException ex) {
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a valid browser push subscription.");
    }
    if (!safePushEndpoint(endpoint) || !validSubscriptionKeys(input.p256dh(), input.auth()))
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a valid browser push subscription.");
    jdbc.update("""
        insert into push_subscriptions(user_id, endpoint, p256dh_key, auth_secret)
        values (?, ?, ?, ?)
        on conflict (user_id, endpoint) do update set p256dh_key = excluded.p256dh_key,
          auth_secret = excluded.auth_secret, revoked_at = null
        """, user.getId(), endpoint.toString(), input.p256dh(), input.auth());
    return subscriptionStatus(user.getId());
  }

  @Transactional
  public SubscriptionStatus revokeSubscription(long subscriptionId) {
    var user = requireStaff();
    jdbc.update("update push_subscriptions set revoked_at = now() where id = ? and user_id = ? and revoked_at is null",
        subscriptionId, user.getId());
    return subscriptionStatus(user.getId());
  }

  @Transactional
  public SubscriptionStatus revokeAllSubscriptions() {
    var user = requireStaff();
    jdbc.update("update push_subscriptions set revoked_at = now() where user_id = ? and revoked_at is null", user.getId());
    return subscriptionStatus(user.getId());
  }

  public SubscriptionStatus subscriptions() {
    var user = requireStaff();
    return subscriptionStatus(user.getId());
  }

  /** Called after committed care-task writes; due/status/owner changes replace pending reminder schedules. */
  @Transactional
  public void taskChanged(long taskId) {
    var tasks = jdbc.query("""
        select id, department_id, assigned_user_id, due_at, status, dependency_state
          from care_tasks where id = ?
        """, TASK, taskId);
    for (var task : tasks) reconcileTask(task.id(), task.departmentId(), task.assignedUserId(), task.dueAt(),
        task.status(), task.dependencyState());
  }

  /** Periodic repair pass makes reminder scheduling resilient to restarts and missed in-process events. */
  @Transactional
  public int reconcileAndDeliverDue() {
    var tasks = jdbc.query("""
        select t.id, t.department_id, t.assigned_user_id, t.due_at, t.status, t.dependency_state
          from care_tasks t
          join task_reminder_preferences p on p.user_id = t.assigned_user_id and p.opted_in = true
         where t.due_at is not null
        """, TASK, new Object[0]);
    for (var task : tasks) reconcileTask(task.id(), task.departmentId(), task.assignedUserId(), task.dueAt(),
        task.status(), task.dependencyState());
    cancelStaleReminders();
    return deliverDue();
  }

  @Transactional
  public void reconcileTask(long taskId, long departmentId, Long assignedUserId, Instant dueAt, String status,
      String dependencyState) {
    if (assignedUserId == null || dueAt == null || !"READY".equals(dependencyState)
        || !("OPEN".equals(status) || "IN_PROGRESS".equals(status))) {
      cancelPending(taskId, null, "TASK_NOT_REMINDABLE");
      return;
    }
    var pref = loadPreferences(assignedUserId);
    if (!pref.optedIn()) {
      cancelPending(taskId, assignedUserId, "OPTED_OUT");
      return;
    }
    Timestamp scheduled = Timestamp.from(dueAt.minusSeconds(pref.minutesBefore() * 60L));
    jdbc.update("""
        update task_reminders set status = 'CANCELLED', error_code = 'TASK_RESCHEDULED', updated_at = now()
         where task_id = ? and status in ('PENDING', 'FAILED') and due_at <> ?
        """, taskId, Timestamp.from(dueAt));
    jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'TASK_REASSIGNED', updated_at = now()"
        + " where task_id = ? and recipient_user_id <> ? and status in ('PENDING', 'FAILED')", taskId, assignedUserId);
    jdbc.update("""
        insert into task_reminders(department_id, task_id, recipient_user_id, due_at, scheduled_at,
          action_token, status, next_attempt_at)
        values (?, ?, ?, ?, ?, ?, 'PENDING', ?)
        on conflict (task_id, recipient_user_id, due_at) do update set
          department_id = excluded.department_id,
          scheduled_at = case when task_reminders.snoozed_until > now()
                              then task_reminders.snoozed_until else excluded.scheduled_at end,
          next_attempt_at = case when task_reminders.snoozed_until > now()
                                 then task_reminders.snoozed_until else excluded.next_attempt_at end,
          status = case when task_reminders.status = 'CANCELLED' then 'PENDING'
                        else task_reminders.status end,
          error_code = null, updated_at = now()
        """, departmentId, taskId, assignedUserId, Timestamp.from(dueAt), scheduled,
        UUID.randomUUID(), scheduled);
  }

  @Transactional
  public SnoozeResult snooze(UUID actionToken, int minutes) {
    var user = requireStaff();
    if (minutes < 5 || minutes > 240)
      throw new ApiException(400, "VALIDATION_ERROR", "Snooze for 5 to 240 minutes.");
    var pref = loadPreferences(user.getId());
    if (!pref.optedIn()) throw ApiException.conflict("PUSH_NOT_ENABLED", "Enable task reminders before snoozing.");
    Instant until = Instant.now().plusSeconds(minutes * 60L);
    int updated = jdbc.update("""
        update task_reminders r set status = 'PENDING', scheduled_at = ?, snoozed_until = ?, next_attempt_at = ?,
          error_code = null, updated_at = now()
         where r.action_token = ? and r.recipient_user_id = ?
           and exists (select 1 from care_tasks t where t.id = r.task_id
             and t.department_id = r.department_id and t.assigned_user_id = r.recipient_user_id
             and t.status in ('OPEN', 'IN_PROGRESS') and t.dependency_state = 'READY')
        """, Timestamp.from(until), Timestamp.from(until), Timestamp.from(until), actionToken, user.getId());
    if (updated != 1) throw ApiException.missing();
    return new SnoozeResult(until);
  }

  /** Resolves an opaque push link only for an authenticated assignee in the active department. */
  public OpenReminder open(UUID actionToken) {
    var user = requireStaff();
    long departmentId = DepartmentContext.id();
    var rows = jdbc.query("""
        select r.task_id, r.department_id, r.due_at, t.status
          from task_reminders r join care_tasks t on t.id = r.task_id and t.department_id = r.department_id
         where r.action_token = ? and r.recipient_user_id = ? and r.department_id = ?
           and t.assigned_user_id = ? and t.status in ('OPEN', 'IN_PROGRESS')
           and t.dependency_state = 'READY'
        """, (rs, n) -> new OpenReminder(rs.getLong("task_id"), rs.getLong("department_id"),
            instant(rs, "due_at"), rs.getString("status")), actionToken, user.getId(), departmentId, user.getId());
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  public List<DeliveryOutcome> outcomes(int limit) {
    var user = requireStaff();
    if (DepartmentContext.id() <= 0) return List.of();
    int capped = Math.max(1, Math.min(100, limit));
    return jdbc.query("""
        select id, status, attempt_count, last_attempt_at, sent_at, error_code, created_at
          from task_reminders where recipient_user_id = ? and department_id = ?
         order by created_at desc, id desc limit ?
        """, DELIVERY, user.getId(), DepartmentContext.id(), capped);
  }

  private int deliverDue() {
    Instant now = Instant.now();
    var due = jdbc.query("""
        select r.id, r.task_id, r.department_id, r.recipient_user_id, r.action_token,
               s.id as subscription_id, s.endpoint, s.p256dh_key, s.auth_secret,
               p.time_zone, p.quiet_hours_start, p.quiet_hours_end, r.attempt_count
          from task_reminders r
          join task_reminder_preferences p on p.user_id = r.recipient_user_id and p.opted_in = true
          join push_subscriptions s on s.user_id = r.recipient_user_id and s.revoked_at is null
          join care_tasks t on t.id = r.task_id and t.department_id = r.department_id
            and t.assigned_user_id = r.recipient_user_id and t.status in ('OPEN', 'IN_PROGRESS')
            and t.dependency_state = 'READY'
         where r.status in ('PENDING', 'FAILED') and (r.status <> 'FAILED' or r.attempt_count < ?)
           and r.scheduled_at <= ? and r.next_attempt_at <= ?
         order by r.scheduled_at, r.id limit 100
        """, DELIVERY_ROW, MAX_ATTEMPTS, Timestamp.from(now), Timestamp.from(now));
    int sent = 0;
    for (var row : due) {
      var quietEnd = quietHoursEnd(row.timeZone(), row.quietStart(), row.quietEnd(), now);
      if (quietEnd != null) {
        jdbc.update("update task_reminders set scheduled_at = ?, next_attempt_at = ?, status = 'PENDING', updated_at = now()"
            + " where id = ? and status in ('PENDING', 'FAILED')", Timestamp.from(quietEnd), Timestamp.from(quietEnd), row.id());
        continue;
      }
      Instant attemptAt = Instant.now();
      int claimed = jdbc.update("""
          update task_reminders set status = 'SENDING', attempt_count = attempt_count + 1,
            last_attempt_at = ?, updated_at = now()
           where id = ? and status in ('PENDING', 'FAILED') and scheduled_at <= ? and next_attempt_at <= ?
          """, Timestamp.from(attemptAt), row.id(), Timestamp.from(attemptAt), Timestamp.from(attemptAt));
      if (claimed != 1) continue;
      try {
        send(row);
        jdbc.update("update task_reminders set status = 'SENT', sent_at = now(), snoozed_until = null, error_code = null, updated_at = now()"
            + " where id = ? and status = 'SENDING'", row.id());
        jdbc.update("update push_subscriptions set last_used_at = now() where id = ?", row.subscriptionId());
        sent++;
      } catch (PushFailure failure) {
        if (failure.gone()) {
          jdbc.update("update push_subscriptions set revoked_at = now() where id = ?", row.subscriptionId());
          jdbc.update("update task_reminders set status = 'PENDING', next_attempt_at = ?, error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
              + " where id = ? and status = 'SENDING'", Timestamp.from(Instant.now()), row.id());
        } else {
          int attempts = row.attemptCount() + 1;
          String state = attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING";
          jdbc.update("update task_reminders set status = ?, error_code = ?, next_attempt_at = ?, updated_at = now()"
              + " where id = ? and status = 'SENDING'", state, failure.code(),
              Timestamp.from(Instant.now().plusMillis(RETRY_DELAY_MS)), row.id());
          // Log only a fixed code; subscription endpoints and task data are deliberately excluded.
          log.warn("Task reminder push delivery failed with code {}.", failure.code());
        }
      }
    }
    return sent;
  }

  private void send(DeliveryRow row) {
    try {
      if (!pushAvailable()) throw new PushFailure("PUSH_UNAVAILABLE", 0);
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
        Security.addProvider(new BouncyCastleProvider());
      byte[] payload = json.writeValueAsBytes(new PushPayload(GENERIC_TITLE, GENERIC_BODY,
          "/tasks?reminder=" + row.actionToken(), row.actionToken().toString()));
      var notification = new Notification(row.endpoint(), decodeKey(row.p256dh()), decode(row.auth()), payload, 300);
      var response = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject).send(notification);
      int status = response.getStatusLine().getStatusCode();
      if (status == 404 || status == 410) throw new PushFailure("SUBSCRIPTION_GONE", status);
      if (status < 200 || status >= 300) throw new PushFailure("PUSH_PROVIDER_ERROR", status);
    } catch (PushFailure failure) {
      throw failure;
    } catch (Exception exception) {
      throw new PushFailure("PUSH_PROVIDER_ERROR", 0);
    }
  }

  private void reconcileForUser(long userId) {
    var tasks = jdbc.query("""
        select id, department_id, assigned_user_id, due_at, status, dependency_state
          from care_tasks where assigned_user_id = ? and due_at is not null
        """, TASK, userId);
    for (var task : tasks) reconcileTask(task.id(), task.departmentId(), task.assignedUserId(), task.dueAt(),
        task.status(), task.dependencyState());
  }

  private void cancelStaleReminders() {
    jdbc.update("""
        update task_reminders r set status = 'CANCELLED', error_code = 'TASK_NOT_REMINDABLE', updated_at = now()
         where r.status in ('PENDING', 'FAILED') and not exists (
           select 1 from care_tasks t where t.id = r.task_id and t.department_id = r.department_id
             and t.assigned_user_id = r.recipient_user_id and t.due_at = r.due_at
             and t.status in ('OPEN', 'IN_PROGRESS') and t.dependency_state = 'READY')
        """);
  }

  private void cancelPending(long taskId, Long userId, String code) {
    String sql = "update task_reminders set status = 'CANCELLED', error_code = ?, updated_at = now()"
        + " where task_id = ? and status in ('PENDING', 'FAILED')";
    if (userId == null) jdbc.update(sql, code, taskId);
    else jdbc.update(sql + " and recipient_user_id = ?", code, taskId, userId);
  }

  private Preferences loadPreferences(long userId) {
    var rows = jdbc.query("""
        select opted_in, time_zone, minutes_before, quiet_hours_start, quiet_hours_end, operational_alerts
          from task_reminder_preferences where user_id = ?
        """, PREFS, userId);
    return rows.isEmpty() ? new Preferences(false, "UTC", 10, null, null, false, pushAvailable(), 0)
        : rows.getFirst().withDelivery(pushAvailable(), subscriptionCount(userId));
  }

  private SubscriptionStatus subscriptionStatus(long userId) {
    Integer count = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ? and revoked_at is null",
        Integer.class, userId);
    return new SubscriptionStatus(count != null && count > 0, count == null ? 0 : count, pushAvailable());
  }

  private int subscriptionCount(long userId) { return subscriptionStatus(userId).activeCount(); }

  private boolean pushAvailable() { return !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank(); }

  private com.example.hospital.domain.AppUser requireStaff() {
    var user = actor.user();
    if (!Set.of("ADMIN", "MEDICAL_STAFF", "DOCTOR").contains(user.getRole()))
      throw new org.springframework.security.access.AccessDeniedException("Department staff required");
    if (DepartmentContext.id() <= 0) throw new org.springframework.security.access.AccessDeniedException("Department context required");
    return user;
  }

  static boolean safePushEndpoint(URI endpoint) {
    if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
        || endpoint.getUserInfo() != null || endpoint.getPort() != -1 && endpoint.getPort() != 443) return false;
    String host = endpoint.getHost().toLowerCase(java.util.Locale.ROOT);
    return host.equals("fcm.googleapis.com") || host.endsWith(".push.services.mozilla.com")
        || host.endsWith(".push.apple.com") || host.endsWith(".notify.windows.com");
  }

  private static boolean validSubscriptionKeys(String p256dh, String auth) {
    try { return decode(p256dh).length == 65 && decode(auth).length == 16; }
    catch (RuntimeException ex) { return false; }
  }

  private static byte[] decode(String value) {
    try { return Base64.getDecoder().decode(value); }
    catch (IllegalArgumentException ignored) { return Base64.getUrlDecoder().decode(value); }
  }

  private static java.security.PublicKey decodeKey(String key) throws Exception {
    var provider = new BouncyCastleProvider();
    var curve = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("secp256r1");
    var point = curve.getCurve().decodePoint(decode(key));
    var spec = new org.bouncycastle.jce.spec.ECPublicKeySpec(point, curve);
    return java.security.KeyFactory.getInstance("ECDH", provider).generatePublic(spec);
  }

  static Instant quietHoursEnd(String zoneName, LocalTime start, LocalTime end, Instant now) {
    if (start == null || end == null || start.equals(end)) return null;
    ZoneId zone = ZoneId.of(zoneName);
    ZonedDateTime localNow = now.atZone(zone);
    LocalTime time = localNow.toLocalTime();
    boolean quiet = start.isBefore(end) ? !time.isBefore(start) && time.isBefore(end)
        : !time.isBefore(start) || time.isBefore(end);
    if (!quiet) return null;
    var targetDate = start.isBefore(end) || !time.isBefore(start)
        ? localNow.toLocalDate().plusDays(start.isBefore(end) ? 0 : 1)
        : localNow.toLocalDate();
    return ZonedDateTime.of(targetDate, end, zone).toInstant();
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column); return rs.wasNull() ? null : value;
  }
  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
  }

  public record Preferences(boolean optedIn, String timeZone, int minutesBefore, LocalTime quietHoursStart,
      LocalTime quietHoursEnd, boolean operationalAlerts, boolean pushAvailable, int activeSubscriptions) {
    Preferences withDelivery(boolean available, int count) {
      return new Preferences(optedIn, timeZone, minutesBefore, quietHoursStart, quietHoursEnd,
          operationalAlerts, available, count);
    }
  }
  public record SubscriptionStatus(boolean subscribed, int activeCount, boolean pushAvailable) {}
  public record SnoozeResult(Instant scheduledAt) {}
  public record OpenReminder(long taskId, long departmentId, Instant dueAt, String status) {}
  public record DeliveryOutcome(long id, String status, int attemptCount, Instant lastAttemptAt,
      Instant sentAt, String errorCode, Instant createdAt) {}
  record PushPayload(String title, String body, String url, String reminderToken) {}
  private record Task(long id, long departmentId, Long assignedUserId, Instant dueAt, String status, String dependencyState) {}
  private record DeliveryRow(long id, long taskId, long departmentId, long recipientUserId, UUID actionToken,
      long subscriptionId, String endpoint, String p256dh, String auth, String timeZone,
      LocalTime quietStart, LocalTime quietEnd, int attemptCount) {}
  private static final RowMapper<Task> TASK = (rs, n) -> new Task(rs.getLong("id"), rs.getLong("department_id"),
      nullableLong(rs, "assigned_user_id"), instant(rs, "due_at"), rs.getString("status"), rs.getString("dependency_state"));
  private static final RowMapper<Preferences> PREFS = (rs, n) -> new Preferences(rs.getBoolean("opted_in"),
      rs.getString("time_zone"), rs.getInt("minutes_before"), rs.getObject("quiet_hours_start", LocalTime.class),
      rs.getObject("quiet_hours_end", LocalTime.class), rs.getBoolean("operational_alerts"), false, 0);
  private static final RowMapper<DeliveryOutcome> DELIVERY = (rs, n) -> new DeliveryOutcome(rs.getLong("id"),
      rs.getString("status"), rs.getInt("attempt_count"), instant(rs, "last_attempt_at"),
      instant(rs, "sent_at"), rs.getString("error_code"), instant(rs, "created_at"));
  private static final RowMapper<DeliveryRow> DELIVERY_ROW = (rs, n) -> new DeliveryRow(rs.getLong("id"),
      rs.getLong("task_id"), rs.getLong("department_id"), rs.getLong("recipient_user_id"),
      rs.getObject("action_token", UUID.class), rs.getLong("subscription_id"), rs.getString("endpoint"),
      rs.getString("p256dh_key"), rs.getString("auth_secret"), rs.getString("time_zone"),
      rs.getObject("quiet_hours_start", LocalTime.class), rs.getObject("quiet_hours_end", LocalTime.class),
      rs.getInt("attempt_count"));

  private static final class PushFailure extends RuntimeException {
    private final String code;
    private final int status;
    PushFailure(String code, int status) { this.code = code; this.status = status; }
    String code() { return code; }
    boolean gone() { return status == 404 || status == 410; }
  }
}
