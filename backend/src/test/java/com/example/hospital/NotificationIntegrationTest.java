package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Notification inbox, read state, deduplication, escalation and policy (#303 #304 #310 #317 #320). */
class NotificationIntegrationTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired NotificationService notifications;

  ResultActions call(String who, String method, String path, Object body, Long department)
      throws Exception {
    MockHttpServletRequestBuilder request =
        switch (method) {
          case "POST" -> post(path);
          case "PUT" -> put(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    request.with(user(who)).with(csrf());
    if (department != null) request.header("X-Department-Id", department);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  JsonNode notice(String who, long id, Long department) throws Exception {
    return result(call(who, "GET", "/api/v1/notifications/" + id, null, department), 200);
  }

  long roomAlert(long department, long roomId) {
    return jdbc.queryForObject(
        "select id from notifications where department_id = ? and dedupe_key = ? and status = 'OPEN'",
        Long.class,
        department,
        "capacity:room:" + roomId);
  }

  long openRoomAlerts(long department, long roomId) {
    return jdbc.queryForObject(
        "select count(*) from notifications where department_id = ? and dedupe_key = ? and status = 'OPEN'",
        Long.class,
        department,
        "capacity:room:" + roomId);
  }

  long newDepartment() throws Exception {
    return result(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Alert Clinic " + unique(), "departmentName", "Ward"),
                1L),
            201)
        .get("departmentId")
        .asLong();
  }

  long doctorIn(long department) throws Exception {
    return result(
            call(
                "admin",
                "POST",
                "/api/v1/doctors",
                Map.of(
                    "doctorIdentifier", "ALERT-" + unique(),
                    "firstName", "Ana",
                    "lastName", "Petrova",
                    "specialty", "Internal medicine",
                    "active", true),
                department),
            201)
        .get("id")
        .asLong();
  }

  long fullRoomIn(long department, long doctorId) throws Exception {
    long roomId =
        result(
                call(
                    "admin",
                    "POST",
                    "/api/v1/rooms",
                    Map.of("roomNumber", "A-" + unique(), "bedCount", 1, "active", true),
                    department),
                201)
            .get("id")
            .asLong();
    long patientId =
        result(
                call(
                    "admin",
                    "POST",
                    "/api/v1/patients",
                    Map.of(
                        "patientIdentifier", "ALERT-" + unique(),
                        "firstName", "Alert",
                        "lastName", "Patient",
                        "dateOfBirth", "1975-05-05"),
                    department),
                201)
            .get("id")
            .asLong();
    result(
        call(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of("patientId", patientId, "doctorId", doctorId, "roomId", roomId),
            department),
        201);
    return roomId;
  }

  Map<String, Object> policy(long version, String minimumSeverity, boolean capacityAlerts) {
    Map<String, Object> body = new HashMap<>();
    body.put("capacityAlertsEnabled", capacityAlerts);
    body.put("activityNoticesEnabled", true);
    body.put("warningPercent", 75);
    body.put("criticalPercent", 90);
    body.put("escalationEnabled", true);
    body.put("escalationMinSeverity", minimumSeverity);
    body.put("acknowledgementMinutes", 5);
    body.put("escalationRole", "ADMIN");
    body.put("retentionDays", 14);
    body.put("version", version);
    return body;
  }

  @Test
  void admissionRaisesItsAlertWithoutWaitingForTheSweep() throws Exception {
    var room = room(1);
    admit(createPatient(), room);

    assertThat(openRoomAlerts(1L, room.get("id").asLong())).isOne();
  }

  @Test
  void fullRoomKeepsOneOpenAlertAndResolvesWhenABedFrees() throws Exception {
    var room = room(1);
    long roomId = room.get("id").asLong();
    var admission = admit(createPatient(), room);
    notifications.evaluateCapacity(1L);
    long alert = roomAlert(1L, roomId);

    notifications.evaluateCapacity(1L);
    notifications.evaluateCapacity(1L);

    assertThat(openRoomAlerts(1L, roomId)).isOne();
    var shown = notice("staff", alert, null);
    assertThat(shown.get("type").asText()).isEqualTo("ROOM_FULL");
    assertThat(shown.get("category").asText()).isEqualTo("CAPACITY");
    assertThat(shown.get("status").asText()).isEqualTo("OPEN");
    assertThat(shown.get("sourceId").asLong()).isEqualTo(roomId);
    assertThat(shown.get("personal").asBoolean()).isFalse();

    result(
        request(
            "admin",
            "POST",
            "/api/v1/admissions/" + admission.get("id").asLong() + "/discharge",
            Map.of("version", admission.get("version").asLong())),
        200);
    notifications.evaluateCapacity(1L);

    var resolved = notice("staff", alert, null);
    assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
    assertThat(resolved.get("resolvedAt").isNull()).isFalse();

    admit(createPatient(), room);
    notifications.evaluateCapacity(1L);

    assertThat(openRoomAlerts(1L, roomId)).isOne();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notifications where dedupe_key = ?",
                Long.class,
                "capacity:room:" + roomId))
        .isEqualTo(2);
  }

  @Test
  void readStateIsTrackedPerAccount() throws Exception {
    var room = room(1);
    admit(createPatient(), room);
    notifications.evaluateCapacity(1L);
    long alert = roomAlert(1L, room.get("id").asLong());

    assertThat(notice("admin", alert, null).get("read").asBoolean()).isFalse();
    result(call("admin", "POST", "/api/v1/notifications/" + alert + "/read", null, null), 200);
    assertThat(notice("admin", alert, null).get("read").asBoolean()).isTrue();
    assertThat(notice("staff", alert, null).get("read").asBoolean()).isFalse();

    long unreadBefore =
        result(call("staff", "GET", "/api/v1/notifications/counts", null, null), 200)
            .get("unread")
            .asLong();
    var marked =
        result(
            call("staff", "POST", "/api/v1/notifications/read-all?category=CAPACITY", null, null),
            200);
    assertThat(marked.get("marked").asLong()).isPositive();
    assertThat(notice("staff", alert, null).get("read").asBoolean()).isTrue();
    long unreadAfter =
        result(call("staff", "GET", "/api/v1/notifications/counts", null, null), 200)
            .get("unread")
            .asLong();
    assertThat(unreadAfter).isLessThan(unreadBefore);

    result(call("admin", "DELETE", "/api/v1/notifications/" + alert + "/read", null, null), 200);
    assertThat(notice("admin", alert, null).get("read").asBoolean()).isFalse();
  }

  @Test
  void recentActionNoticesArePersistedForTheActorOnly() throws Exception {
    var admission = admit(createPatient(), room(2));
    long admissionId = admission.get("id").asLong();
    Long id =
        jdbc.queryForObject(
            "select max(id) from notifications where category = 'ACTIVITY'"
                + " and type = 'ADMISSION_CREATED' and source_id = ?",
            Long.class,
            admissionId);
    assertThat(id).isNotNull();

    var mine = notice("admin", id, null);
    assertThat(mine.get("title").asText()).isEqualTo("Admission created");
    assertThat(mine.get("sourceType").asText()).isEqualTo("Admission");
    assertThat(mine.get("personal").asBoolean()).isTrue();
    assertThat(mine.get("expiresAt").isNull()).isFalse();

    var latest =
        result(
            call("admin", "GET", "/api/v1/notifications?category=ACTIVITY&size=5", null, null), 200);
    assertThat(latest.get("items").get(0).get("id").asLong()).isEqualTo(id);

    call("staff", "GET", "/api/v1/notifications/" + id, null, null)
        .andExpect(status().isNotFound());
  }

  @Test
  void unacknowledgedAlertEscalatesOnceToTheConfiguredRole() throws Exception {
    long department = newDepartment();
    result(
        call("admin", "PUT", "/api/v1/notifications/policy", policy(0, "WARNING", true), department),
        200);
    long doctorId = doctorIn(department);
    long waitingRoom = fullRoomIn(department, doctorId);
    long handledRoom = fullRoomIn(department, doctorId);
    notifications.evaluateCapacity(department);
    long waiting = roomAlert(department, waitingRoom);
    long handled = roomAlert(department, handledRoom);

    assertThat(notice("admin", waiting, department).get("requiresAcknowledgement").asBoolean())
        .isTrue();
    var acknowledged =
        result(
            call("admin", "POST", "/api/v1/notifications/" + handled + "/acknowledge", null, department),
            200);
    assertThat(acknowledged.get("acknowledgedAt").isNull()).isFalse();
    call("admin", "POST", "/api/v1/notifications/" + handled + "/acknowledge", null, department)
        .andExpect(status().isConflict());
    result(call("admin", "POST", "/api/v1/notifications/" + waiting + "/read", null, department), 200);

    jdbc.update(
        "update notifications set acknowledgement_due_at = now() - interval '1 minute'"
            + " where department_id = ? and status = 'OPEN'",
        department);
    notifications.escalateDue(department);
    notifications.escalateDue(department);

    var escalated = notice("admin", waiting, department);
    assertThat(escalated.get("escalatedAt").isNull()).isFalse();
    assertThat(escalated.get("escalatedToRole").asText()).isEqualTo("ADMIN");
    assertThat(escalated.get("escalatedToYou").asBoolean()).isTrue();
    assertThat(escalated.get("read").asBoolean()).isFalse();
    assertThat(notice("admin", handled, department).get("escalatedAt").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where event_type = 'NOTIFICATION_ESCALATED'"
                    + " and entity_id = ? and source = 'SYSTEM'",
                Long.class,
                waiting))
        .isOne();
    // Two full single-bed rooms also put the whole department at 100%, a second unacknowledged alert.
    long expected =
        jdbc.queryForObject(
            "select count(*) from notifications where department_id = ? and status = 'OPEN'"
                + " and escalated_at is not null and acknowledged_at is null",
            Long.class,
            department);
    assertThat(expected).isEqualTo(2);
    assertThat(
            result(call("admin", "GET", "/api/v1/notifications/counts", null, department), 200)
                .get("escalatedToYou")
                .asLong())
        .isEqualTo(expected);
  }

  @Test
  void onlyStaffCanAcknowledgeAndOnlyAlertsThatNeedIt() throws Exception {
    var room = room(1);
    admit(createPatient(), room);
    notifications.evaluateCapacity(1L);
    long alert = roomAlert(1L, room.get("id").asLong());

    call("doctor", "POST", "/api/v1/notifications/" + alert + "/acknowledge", null, null)
        .andExpect(status().isForbidden());
    // The default policy asks for acknowledgement from CRITICAL; a full room is a WARNING.
    var rejected =
        result(call("staff", "POST", "/api/v1/notifications/" + alert + "/acknowledge", null, null), 409);
    assertThat(rejected.get("code").asText()).isEqualTo("NOT_ACKNOWLEDGEABLE");
    assertThat(notice("doctor", alert, null).get("status").asText()).isEqualTo("OPEN");
  }

  @Test
  void policyIsAdminOnlyValidatedVersionedAndAudited() throws Exception {
    long department = newDepartment();
    var defaults = result(call("admin", "GET", "/api/v1/notifications/policy", null, department), 200);
    assertThat(defaults.get("configured").asBoolean()).isFalse();
    assertThat(defaults.get("version").asLong()).isZero();
    assertThat(defaults.get("channels").get(0).asText()).isEqualTo("IN_APP");
    assertThat(defaults.get("warningPercent").asInt()).isEqualTo(75);
    assertThat(defaults.get("criticalPercent").asInt()).isEqualTo(90);

    call("staff", "PUT", "/api/v1/notifications/policy", policy(0, "CRITICAL", true), null)
        .andExpect(status().isForbidden());

    var inverted = policy(0, "CRITICAL", true);
    inverted.put("warningPercent", 90);
    inverted.put("criticalPercent", 80);
    assertThat(
            result(call("admin", "PUT", "/api/v1/notifications/policy", inverted, department), 400)
                .get("code")
                .asText())
        .isEqualTo("INVALID_POLICY");
    var badRole = policy(0, "CRITICAL", true);
    badRole.put("escalationRole", "DOCTOR");
    call("admin", "PUT", "/api/v1/notifications/policy", badRole, department)
        .andExpect(status().isBadRequest());

    var saved =
        result(
            call("admin", "PUT", "/api/v1/notifications/policy", policy(0, "CRITICAL", true), department),
            200);
    assertThat(saved.get("configured").asBoolean()).isTrue();
    assertThat(saved.get("version").asLong()).isOne();
    assertThat(
            result(
                    call(
                        "admin",
                        "PUT",
                        "/api/v1/notifications/policy",
                        policy(0, "CRITICAL", true),
                        department),
                    409)
                .get("code")
                .asText())
        .isEqualTo("STALE_STATE");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where event_type = 'NOTIFICATION_POLICY_UPDATED'"
                    + " and department_id = ?",
                Long.class,
                department))
        .isOne();

    long roomId = fullRoomIn(department, doctorIn(department));
    notifications.evaluateCapacity(department);
    long alert = roomAlert(department, roomId);
    result(
        call("admin", "PUT", "/api/v1/notifications/policy", policy(1, "CRITICAL", false), department),
        200);
    notifications.evaluateCapacity(department);

    assertThat(notice("admin", alert, department).get("status").asText()).isEqualTo("RESOLVED");
  }

  @Test
  void alertsStayInsideTheirDepartment() throws Exception {
    long department = newDepartment();
    long roomId = fullRoomIn(department, doctorIn(department));
    notifications.evaluateCapacity(department);
    long alert = roomAlert(department, roomId);

    call("admin", "GET", "/api/v1/notifications/" + alert, null, 1L)
        .andExpect(status().isNotFound());
    call("admin", "POST", "/api/v1/notifications/" + alert + "/read", null, 1L)
        .andExpect(status().isNotFound());
    assertThat(notice("admin", alert, department).get("read").asBoolean()).isFalse();
  }
}
