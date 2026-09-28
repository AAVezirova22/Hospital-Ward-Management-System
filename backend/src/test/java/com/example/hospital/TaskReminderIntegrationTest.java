package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.service.TaskReminderService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class TaskReminderIntegrationTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired TaskReminderService reminders;

  @Test
  void reschedulingTaskInvalidatesOldReminderLinkAndSnoozeToken() throws Exception {
    long userId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    jdbc.update("insert into push_subscriptions(user_id, endpoint, p256dh_key, auth_secret) values (?, ?, 'key', 'auth')",
        userId, "https://fcm.googleapis.com/send/" + UUID.randomUUID());
    jdbc.update("insert into push_subscriptions(user_id, endpoint, p256dh_key, auth_secret) values (?, ?, 'key', 'auth')",
        userId, "https://fcm.googleapis.com/send/" + UUID.randomUUID());
    long patientId = jdbc.queryForObject("select min(id) from patients where department_id=1", Long.class);
    String name = "Reminder test " + UUID.randomUUID();
    long templateId = jdbc.queryForObject("""
        insert into care_workflow_templates(department_id, name, draft_definition, created_by)
        values (1, ?, '{}', ?) returning id
        """, Long.class, name, userId);
    long versionId = jdbc.queryForObject("""
        insert into care_workflow_versions(department_id, template_id, version_number, definition, published_by)
        values (1, ?, 1, '{}', ?) returning id
        """, Long.class, templateId, userId);
    long runId = jdbc.queryForObject("""
        insert into care_workflow_runs(department_id, template_id, workflow_version_id, patient_id,
          trigger_type, trigger_source_id, status, triggered_by)
        values (1, ?, ?, ?, 'MANUAL', ?, 'ACTIVE', ?) returning id
        """, Long.class, templateId, versionId, patientId, UUID.randomUUID().toString(), userId);
    Instant originalDue = Instant.now().plusSeconds(3600);
    long taskId = jdbc.queryForObject("""
        insert into care_tasks(department_id, workflow_run_id, template_task_key, title, owner_role,
          assigned_user_id, due_at, status, dependency_state)
        values (1, ?, 'follow-up', 'Review follow-up', 'ADMIN', ?, ?, 'OPEN', 'READY') returning id
        """, Long.class, runId, userId, Timestamp.from(originalDue));

    Map<String, Object> preferences = new HashMap<>();
    preferences.put("optedIn", true);
    preferences.put("timeZone", "UTC");
    preferences.put("minutesBefore", 0);
    preferences.put("quietHoursStart", null);
    preferences.put("quietHoursEnd", null);
    preferences.put("operationalAlerts", false);
    result(request("admin", "PUT", "/api/v1/task-reminders/preferences", preferences, 1L), 200);

    var tokens = jdbc.queryForList("select action_token from task_reminders where task_id=? order by id", UUID.class, taskId);
    assertThat(tokens).hasSize(2);
    jdbc.update("update care_tasks set due_at=? where id=?", Timestamp.from(originalDue.plusSeconds(7200)), taskId);

    request("admin", "GET", "/api/v1/task-reminders/open/" + tokens.getFirst(), null, 1L)
        .andExpect(status().isNotFound());
    request("admin", "POST", "/api/v1/task-reminders/snooze/" + tokens.getFirst(), Map.of("minutes", 15), 1L)
        .andExpect(status().isNotFound());
  }

  @Test
  void reminderAndSubscriptionChangesAreAuditedWithoutSecrets() throws Exception {
    long userId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    String secretEndpoint = "https://fcm.googleapis.com/send/" + UUID.randomUUID();

    Map<String, Object> optedIn = new HashMap<>();
    optedIn.put("optedIn", true);
    optedIn.put("timeZone", "UTC");
    optedIn.put("minutesBefore", 15);
    optedIn.put("quietHoursStart", null);
    optedIn.put("quietHoursEnd", null);
    optedIn.put("operationalAlerts", false);
    result(request("admin", "PUT", "/api/v1/task-reminders/preferences", optedIn, 1L), 200);
    // subscribe() needs push configured, so the row is inserted directly here; the audited
    // write under test is the preference change and the revocation below.
    jdbc.update("insert into push_subscriptions(user_id, endpoint, p256dh_key, auth_secret)"
        + " values (?, ?, 'test-p256dh-value', 'test-auth-secret')", userId, secretEndpoint);
    long subscriptionId = jdbc.queryForObject(
        "select id from push_subscriptions where user_id=? and endpoint=?", Long.class, userId, secretEndpoint);

    // Opting out of phone reminders is itself an auditable act.
    Map<String, Object> optedOut = new HashMap<>(optedIn);
    optedOut.put("optedIn", false);
    result(request("admin", "PUT", "/api/v1/task-reminders/preferences", optedOut, 1L), 200);
    result(request("admin", "DELETE", "/api/v1/task-reminders/subscriptions/" + subscriptionId, null, 1L), 200);

    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type='TASK_REMINDER_PREFERENCES_UPDATED'"
            + " and user_id=?", Long.class, userId)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type='PUSH_SUBSCRIPTION_REVOKED' and entity_id=?",
        Long.class, subscriptionId)).isEqualTo(1);

    // The audit records the fact of the change, never a task, patient or department.
    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='PUSH_SUBSCRIPTION_REVOKED' order by id desc limit 1",
        String.class);
    assertThat(metadata).contains("SUBSCRIPTION_REVOKED");
    assertThat(metadata).doesNotContain(secretEndpoint);
    assertThat(metadata).doesNotContain("test-p256dh-value");
    assertThat(metadata).doesNotContain("test-auth-secret");
  }

  @Test
  void strandedSendingRemindersAreReclaimedByTheDeliverySweep() throws Exception {
    long userId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    // A reminder claimed by a process that then crashed stays SENDING forever: the delivery
    // sweep only selects PENDING/FAILED, so it is never retried and never cancelled.
    Instant due = Instant.now().plusSeconds(1800);
    long taskId = jdbc.queryForObject("""
        insert into care_tasks(department_id, workflow_run_id, template_task_key, title, owner_role,
          assigned_user_id, due_at, status, dependency_state)
        select 1, (select min(id) from care_workflow_runs where department_id = 1), 'stranded-' || ?,
          'Stranded reminder task', 'ADMIN', ?, ?, 'OPEN', 'READY' returning id
        """, Long.class, UUID.randomUUID(), userId, Timestamp.from(due));
    long stranded = jdbc.queryForObject("""
        insert into task_reminders(department_id, task_id, recipient_user_id, due_at, scheduled_at,
          action_token, status, attempt_count, last_attempt_at, next_attempt_at)
        values (1, ?, ?, ?, ?, gen_random_uuid(), 'SENDING', 1, now() - interval '1 hour', now())
        returning id
        """, Long.class, taskId, userId, Timestamp.from(due), Timestamp.from(due));
    long live = jdbc.queryForObject("""
        insert into task_reminders(department_id, task_id, recipient_user_id, due_at, scheduled_at,
          action_token, status, attempt_count, last_attempt_at, next_attempt_at)
        values (1, ?, ?, ?, ?, gen_random_uuid(), 'SENDING', 1, now(), now())
        returning id
        """, Long.class, taskId, userId, Timestamp.from(due), Timestamp.from(due));

    reminders.reconcileAndDeliverDue();

    // The expired lease is reclaimed so the reminder is retried rather than abandoned; a
    // still-valid lease held by a live send must not be stolen.
    assertThat(jdbc.queryForObject("select status from task_reminders where id=?", String.class, stranded))
        .isNotEqualTo("SENDING");
    assertThat(jdbc.queryForObject("select status from task_reminders where id=?", String.class, live))
        .isEqualTo("SENDING");
  }
}
