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
  private static final String OPERATIONAL_TITLE = "Department alert";
  private static final String OPERATIONAL_BODY = "A department update needs review. Sign in to view it.";

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final ObjectMapper json;
  private final String vapidPublicKey;
  private final String vapidPrivateKey;
  private final String vapidSubject;
  private final boolean schedulerEnabled;

  public TaskReminderService(
      JdbcTemplate jdbc,
      Actor actor,
      ObjectMapper json,
      @Value("${app.push.vapid-public-key:}") String vapidPublicKey,
      @Value("${app.push.vapid-private-key:}") String vapidPrivateKey,
      @Value("${app.push.vapid-subject:mailto:admin@example.invalid}") String vapidSubject,
      @Value("${app.task-reminders.enabled:false}") boolean schedulerEnabled) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.json = json;
    this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.strip();
    this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.strip();
    this.vapidSubject = vapidSubject;
    this.schedulerEnabled = schedulerEnabled;
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
      jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'OPTED_OUT', updated_at = now()"
          + " where recipient_user_id = ? and status in ('PENDING', 'FAILED')", user.getId());
    } else {
      if (!input.operationalAlerts())
        jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'EVENTS_DISABLED', updated_at = now()"
            + " where recipient_user_id = ? and status in ('PENDING', 'FAILED')", user.getId());
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
    reconcileForUser(user.getId());
    return subscriptionStatus(user.getId());
  }

  @Transactional
  public SubscriptionStatus revokeSubscription(long subscriptionId) {
    var user = requireStaff();
    jdbc.update("update push_subscriptions set revoked_at = now() where id = ? and user_id = ? and revoked_at is null",
        subscriptionId, user.getId());
    jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
        + " where subscription_id = ? and recipient_user_id = ? and status in ('PENDING', 'FAILED')",
        subscriptionId, user.getId());
    jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
        + " where subscription_id = ? and recipient_user_id = ? and status in ('PENDING', 'FAILED')",
        subscriptionId, user.getId());
    return subscriptionStatus(user.getId());
  }

  @Transactional
  public SubscriptionStatus revokeAllSubscriptions() {
    var user = requireStaff();
    jdbc.update("update push_subscriptions set revoked_at = now() where user_id = ? and revoked_at is null", user.getId());
    jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'SUBSCRIPTIONS_REVOKED', updated_at = now()"
        + " where recipient_user_id = ? and status in ('PENDING', 'FAILED')", user.getId());
    jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'SUBSCRIPTIONS_REVOKED', updated_at = now()"
        + " where recipient_user_id = ? and status in ('PENDING', 'FAILED')", user.getId());
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

  /** Queue a generic push for an in-app notice, without copying its title, entity, or detail. */
  @Transactional
  public void queueOperationalNotice(long departmentId, long notificationId, Long recipientUserId) {
    if (departmentId <= 0 || !pushAvailable()) return;
    var versions = jdbc.queryForList("select version from notifications where id = ? and department_id = ?"
        + " and category in ('CAPACITY', 'ACTIVITY') and status in ('OPEN', 'INFO')"
        + " and (expires_at is null or expires_at > now())", Long.class, notificationId, departmentId);
    if (versions.isEmpty()) return;
    long version = versions.getFirst();
    jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'NOTICE_UPDATED', updated_at = now()"
        + " where notification_id = ? and notice_version <> ? and status in ('PENDING', 'FAILED')", notificationId, version);
    var targets = jdbc.query("""
        select distinct u.id as user_id, s.id as subscription_id
          from department_memberships m
          join app_users u on u.id = m.user_id and u.enabled = true
          join task_reminder_preferences p on p.user_id = u.id and p.opted_in = true and p.operational_alerts = true
          join push_subscriptions s on s.user_id = u.id and s.revoked_at is null
         where m.department_id = ? and (? is null or u.id = ?)
         order by u.id, s.id
        """, (rs, n) -> new OperationalTarget(rs.getLong("user_id"), rs.getLong("subscription_id")),
        departmentId, recipientUserId, recipientUserId);
    for (var target : targets) {
      jdbc.update("""
          insert into operational_push_deliveries(department_id, notification_id, recipient_user_id,
            subscription_id, notice_version, action_token, next_attempt_at)
          values (?, ?, ?, ?, ?, ?, now()) on conflict do nothing
          """, departmentId, notificationId, target.userId(), target.subscriptionId(), version, UUID.randomUUID());
    }
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
    return deliverDue() + deliverOperationalDue();
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
    var subscriptions = jdbc.queryForList(
        "select id from push_subscriptions where user_id = ? and revoked_at is null order by id", Long.class,
        assignedUserId);
    jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'NO_ACTIVE_SUBSCRIPTION', updated_at = now()"
        + " where task_id = ? and recipient_user_id = ? and subscription_id is null"
        + " and status in ('PENDING', 'FAILED')", taskId, assignedUserId);
    jdbc.update("""
        update task_reminders set status = 'CANCELLED', error_code = 'TASK_RESCHEDULED', updated_at = now()
         where task_id = ? and status in ('PENDING', 'FAILED') and due_at <> ?
        """, taskId, Timestamp.from(dueAt));
    jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'TASK_REASSIGNED', updated_at = now()"
        + " where task_id = ? and recipient_user_id <> ? and status in ('PENDING', 'FAILED')", taskId, assignedUserId);
    for (long subscriptionId : subscriptions) {
      jdbc.update("""
          insert into task_reminders(department_id, task_id, recipient_user_id, subscription_id,
            due_at, scheduled_at, action_token, status, next_attempt_at)
          values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
          on conflict (task_id, recipient_user_id, due_at, subscription_id) do update set
            department_id = excluded.department_id,
            scheduled_at = case when task_reminders.snoozed_until > now()
                                then task_reminders.snoozed_until else excluded.scheduled_at end,
            next_attempt_at = case when task_reminders.snoozed_until > now()
                                   then task_reminders.snoozed_until else excluded.next_attempt_at end,
            status = case when task_reminders.status = 'CANCELLED' then 'PENDING'
                          else task_reminders.status end,
            error_code = null, updated_at = now()
          """, departmentId, taskId, assignedUserId, subscriptionId, Timestamp.from(dueAt), scheduled,
          UUID.randomUUID(), scheduled);
    }
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
         where r.recipient_user_id = ? and r.status <> 'CANCELLED'
           and exists (select 1 from push_subscriptions s where s.id = r.subscription_id
             and s.user_id = r.recipient_user_id and s.revoked_at is null)
           and exists (select 1 from task_reminders token_row
             join push_subscriptions token_subscription on token_subscription.id = token_row.subscription_id
               and token_subscription.user_id = token_row.recipient_user_id and token_subscription.revoked_at is null
             join care_tasks t on t.id = token_row.task_id and t.department_id = token_row.department_id
               and t.assigned_user_id = token_row.recipient_user_id and t.due_at = token_row.due_at
               and t.status in ('OPEN', 'IN_PROGRESS') and t.dependency_state = 'READY'
            where token_row.action_token = ? and token_row.recipient_user_id = ?
              and token_row.task_id = r.task_id and token_row.due_at = r.due_at)
        """, Timestamp.from(until), Timestamp.from(until), Timestamp.from(until), user.getId(),
        actionToken, user.getId());
    if (updated == 0) throw ApiException.missing();
    return new SnoozeResult(until);
  }

  /** Resolves an opaque push link only for an authenticated assignee in the active department. */
  public OpenReminder open(UUID actionToken) {
    var user = requireStaff();
    long departmentId = DepartmentContext.id();
    var rows = jdbc.query("""
        select r.task_id, r.department_id, r.due_at, t.status
          from task_reminders r join care_tasks t on t.id = r.task_id and t.department_id = r.department_id
          join push_subscriptions s on s.id = r.subscription_id and s.user_id = r.recipient_user_id
            and s.revoked_at is null
         where r.action_token = ? and r.recipient_user_id = ? and r.department_id = ?
           and t.assigned_user_id = ? and t.status in ('OPEN', 'IN_PROGRESS')
           and t.due_at = r.due_at
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

  public OpenOperationalNotification openOperationalNotification(UUID actionToken) {
    var user = requireStaff();
    var rows = jdbc.query("""
        select n.id, n.department_id, n.status
          from operational_push_deliveries d
          join notifications n on n.id = d.notification_id and n.department_id = d.department_id
         where d.action_token = ? and d.recipient_user_id = ? and d.department_id = ?
           and (n.recipient_user_id is null or n.recipient_user_id = ?)
           and n.status in ('OPEN', 'INFO') and (n.expires_at is null or n.expires_at > now())
        """, (rs, n) -> new OpenOperationalNotification(rs.getLong("id"), rs.getLong("department_id"),
            rs.getString("status")), actionToken, user.getId(), DepartmentContext.id(), user.getId());
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  public List<OperationalDeliveryOutcome> operationalOutcomes(int limit) {
    var user = requireStaff();
    if (DepartmentContext.id() <= 0) return List.of();
    return jdbc.query("""
        select id, status, attempt_count, last_attempt_at, sent_at, error_code, created_at
          from operational_push_deliveries where recipient_user_id = ? and department_id = ?
         order by created_at desc, id desc limit ?
        """, OPERATIONAL_DELIVERY, user.getId(), DepartmentContext.id(), Math.max(1, Math.min(100, limit)));
  }

  private int deliverDue() {
    Instant now = Instant.now();
    var due = jdbc.query("""
        select r.id, r.task_id, r.department_id, r.recipient_user_id, r.action_token,
               s.id as subscription_id, s.endpoint, s.p256dh_key, s.auth_secret,
               p.time_zone, p.quiet_hours_start, p.quiet_hours_end, r.attempt_count
          from task_reminders r
          join task_reminder_preferences p on p.user_id = r.recipient_user_id and p.opted_in = true
          join push_subscriptions s on s.id = r.subscription_id and s.user_id = r.recipient_user_id
            and s.revoked_at is null
          join care_tasks t on t.id = r.task_id and t.department_id = r.department_id
            and t.assigned_user_id = r.recipient_user_id and t.status in ('OPEN', 'IN_PROGRESS')
            and t.due_at = r.due_at
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
          jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
              + " where subscription_id = ? and status in ('PENDING', 'FAILED', 'SENDING')", row.subscriptionId());
          jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
              + " where subscription_id = ? and status in ('PENDING', 'FAILED', 'SENDING')", row.subscriptionId());
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

  private int deliverOperationalDue() {
    var now = Instant.now();
    jdbc.update("""
        update operational_push_deliveries d set status = 'CANCELLED', error_code = 'NOTICE_EXPIRED', updated_at = now()
         where d.status in ('PENDING', 'FAILED') and not exists (
           select 1 from notifications n where n.id = d.notification_id and n.department_id = d.department_id
             and n.status in ('OPEN', 'INFO') and (n.expires_at is null or n.expires_at > now()))
        """);
    jdbc.update("""
        update operational_push_deliveries d set status = 'CANCELLED', error_code = 'PUSH_DISABLED', updated_at = now()
         where d.status in ('PENDING', 'FAILED') and not exists (
           select 1 from task_reminder_preferences p where p.user_id = d.recipient_user_id
             and p.opted_in = true and p.operational_alerts = true)
        """);
    var due = jdbc.query("""
        select d.id, d.recipient_user_id, d.subscription_id, d.action_token, d.attempt_count,
               s.endpoint, s.p256dh_key, s.auth_secret
          from operational_push_deliveries d
          join push_subscriptions s on s.id = d.subscription_id and s.user_id = d.recipient_user_id
            and s.revoked_at is null
          join task_reminder_preferences p on p.user_id = d.recipient_user_id
            and p.opted_in = true and p.operational_alerts = true
          join notifications n on n.id = d.notification_id and n.department_id = d.department_id
            and n.status in ('OPEN', 'INFO') and (n.expires_at is null or n.expires_at > ?)
         where d.status in ('PENDING', 'FAILED') and (d.status <> 'FAILED' or d.attempt_count < ?)
           and d.next_attempt_at <= ?
         order by d.next_attempt_at, d.id limit 100
        """, OPERATIONAL_ROW, Timestamp.from(now), MAX_ATTEMPTS, Timestamp.from(now));
    int sent = 0;
    for (var row : due) {
      Instant attemptAt = Instant.now();
      int claimed = jdbc.update("""
          update operational_push_deliveries set status = 'SENDING', attempt_count = attempt_count + 1,
            last_attempt_at = ?, updated_at = now()
           where id = ? and status in ('PENDING', 'FAILED') and next_attempt_at <= ?
          """, Timestamp.from(attemptAt), row.id(), Timestamp.from(attemptAt));
      if (claimed != 1) continue;
      try {
        sendOperational(row);
        jdbc.update("update operational_push_deliveries set status = 'SENT', sent_at = now(), error_code = null, updated_at = now()"
            + " where id = ? and status = 'SENDING'", row.id());
        jdbc.update("update push_subscriptions set last_used_at = now() where id = ?", row.subscriptionId());
        sent++;
      } catch (PushFailure failure) {
        if (failure.gone()) {
          jdbc.update("update push_subscriptions set revoked_at = now() where id = ?", row.subscriptionId());
          jdbc.update("update operational_push_deliveries set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
              + " where subscription_id = ? and status in ('PENDING', 'FAILED', 'SENDING')", row.subscriptionId());
          jdbc.update("update task_reminders set status = 'CANCELLED', error_code = 'SUBSCRIPTION_REVOKED', updated_at = now()"
              + " where subscription_id = ? and status in ('PENDING', 'FAILED', 'SENDING')", row.subscriptionId());
        } else {
          int attempts = row.attemptCount() + 1;
          String state = attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING";
          jdbc.update("update operational_push_deliveries set status = ?, error_code = ?, next_attempt_at = ?, updated_at = now()"
              + " where id = ? and status = 'SENDING'", state, failure.code(),
              Timestamp.from(Instant.now().plusMillis(RETRY_DELAY_MS)), row.id());
          log.warn("Operational push delivery failed with code {}.", failure.code());
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
      sendPayload(row.endpoint(), row.p256dh(), row.auth(), taskPushPayload(row.actionToken()));
    } catch (PushFailure failure) {
      throw failure;
    } catch (Exception exception) {
      throw new PushFailure("PUSH_PROVIDER_ERROR", 0);
    }
  }

  private void sendOperational(OperationalDeliveryRow row) {
    if (!pushAvailable()) throw new PushFailure("PUSH_UNAVAILABLE", 0);
    sendPayload(row.endpoint(), row.p256dh(), row.auth(), operationalPushPayload(row.actionToken()));
  }

  static PushPayload taskPushPayload(UUID token) {
    return new PushPayload("TASK_REMINDER", GENERIC_TITLE, GENERIC_BODY,
        "/app/tasks?reminder=" + token, token.toString(), null);
  }

  static PushPayload operationalPushPayload(UUID token) {
    return new PushPayload("OPERATIONAL_ALERT", OPERATIONAL_TITLE, OPERATIONAL_BODY,
        "/app/dashboard?notification=" + token, null, token.toString());
  }

  private void sendPayload(String endpoint, String p256dh, String auth, PushPayload value) {
    try {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
        Security.addProvider(new BouncyCastleProvider());
      byte[] payload = json.writeValueAsBytes(value);
      var notification = new Notification(endpoint, decodeKey(p256dh), decode(auth), payload, 300);
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
    return rows.isEmpty() ? new Preferences(false, "UTC", 10, null, null, false, pushAvailable(), 0,
        LEAD_MINUTES.stream().sorted().toList())
        : rows.getFirst().withDelivery(pushAvailable(), subscriptionCount(userId));
  }

  private SubscriptionStatus subscriptionStatus(long userId) {
    Integer count = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ? and revoked_at is null",
        Integer.class, userId);
    return new SubscriptionStatus(count != null && count > 0, count == null ? 0 : count, pushAvailable());
  }

  private int subscriptionCount(long userId) { return subscriptionStatus(userId).activeCount(); }

  private boolean pushAvailable() {
    return schedulerEnabled && !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
  }

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
      LocalTime quietHoursEnd, boolean operationalAlerts, boolean pushAvailable, int activeSubscriptions,
      List<Integer> availableLeadMinutes) {
    Preferences withDelivery(boolean available, int count) {
      return new Preferences(optedIn, timeZone, minutesBefore, quietHoursStart, quietHoursEnd,
          operationalAlerts, available, count, LEAD_MINUTES.stream().sorted().toList());
    }
  }
  public record SubscriptionStatus(boolean subscribed, int activeCount, boolean pushAvailable) {}
  public record SnoozeResult(Instant scheduledAt) {}
  public record OpenReminder(long taskId, long departmentId, Instant dueAt, String status) {}
  public record OpenOperationalNotification(long notificationId, long departmentId, String status) {}
  public record DeliveryOutcome(long id, String status, int attemptCount, Instant lastAttemptAt,
      Instant sentAt, String errorCode, Instant createdAt) {}
  public record OperationalDeliveryOutcome(long id, String status, int attemptCount, Instant lastAttemptAt,
      Instant sentAt, String errorCode, Instant createdAt) {}
  record PushPayload(String type, String title, String body, String url,
      String reminderToken, String notificationToken) {}
  private record Task(long id, long departmentId, Long assignedUserId, Instant dueAt, String status, String dependencyState) {}
  private record DeliveryRow(long id, long taskId, long departmentId, long recipientUserId, UUID actionToken,
      long subscriptionId, String endpoint, String p256dh, String auth, String timeZone,
      LocalTime quietStart, LocalTime quietEnd, int attemptCount) {}
  private record OperationalTarget(long userId, long subscriptionId) {}
  private record OperationalDeliveryRow(long id, long recipientUserId, long subscriptionId, UUID actionToken,
      int attemptCount, String endpoint, String p256dh, String auth) {}
  private static final RowMapper<Task> TASK = (rs, n) -> new Task(rs.getLong("id"), rs.getLong("department_id"),
      nullableLong(rs, "assigned_user_id"), instant(rs, "due_at"), rs.getString("status"), rs.getString("dependency_state"));
  private static final RowMapper<Preferences> PREFS = (rs, n) -> new Preferences(rs.getBoolean("opted_in"),
      rs.getString("time_zone"), rs.getInt("minutes_before"), rs.getObject("quiet_hours_start", LocalTime.class),
      rs.getObject("quiet_hours_end", LocalTime.class), rs.getBoolean("operational_alerts"), false, 0,
      LEAD_MINUTES.stream().sorted().toList());
  private static final RowMapper<DeliveryOutcome> DELIVERY = (rs, n) -> new DeliveryOutcome(rs.getLong("id"),
      rs.getString("status"), rs.getInt("attempt_count"), instant(rs, "last_attempt_at"),
      instant(rs, "sent_at"), rs.getString("error_code"), instant(rs, "created_at"));
  private static final RowMapper<DeliveryRow> DELIVERY_ROW = (rs, n) -> new DeliveryRow(rs.getLong("id"),
      rs.getLong("task_id"), rs.getLong("department_id"), rs.getLong("recipient_user_id"),
      rs.getObject("action_token", UUID.class), rs.getLong("subscription_id"), rs.getString("endpoint"),
      rs.getString("p256dh_key"), rs.getString("auth_secret"), rs.getString("time_zone"),
      rs.getObject("quiet_hours_start", LocalTime.class), rs.getObject("quiet_hours_end", LocalTime.class),
      rs.getInt("attempt_count"));
  private static final RowMapper<OperationalDeliveryOutcome> OPERATIONAL_DELIVERY =
      (rs, n) -> new OperationalDeliveryOutcome(rs.getLong("id"), rs.getString("status"),
          rs.getInt("attempt_count"), instant(rs, "last_attempt_at"), instant(rs, "sent_at"),
          rs.getString("error_code"), instant(rs, "created_at"));
  private static final RowMapper<OperationalDeliveryRow> OPERATIONAL_ROW = (rs, n) ->
      new OperationalDeliveryRow(rs.getLong("id"), rs.getLong("recipient_user_id"),
          rs.getLong("subscription_id"), rs.getObject("action_token", UUID.class), rs.getInt("attempt_count"),
          rs.getString("endpoint"), rs.getString("p256dh_key"), rs.getString("auth_secret"));

  private static final class PushFailure extends RuntimeException {
    private final String code;
    private final int status;
    PushFailure(String code, int status) { this.code = code; this.status = status; }
    String code() { return code; }
    boolean gone() { return status == 404 || status == 410; }
  }
}
