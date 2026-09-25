package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
