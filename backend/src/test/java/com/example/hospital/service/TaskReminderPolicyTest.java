package com.example.hospital.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TaskReminderPolicyTest {
  @Test
  void quietHoursHandleNormalAndOvernightWindowsInRecipientZone() {
    Instant insideDayWindow = Instant.parse("2026-09-24T13:00:00Z");
    assertEquals(Instant.parse("2026-09-24T15:00:00Z"), TaskReminderService.quietHoursEnd(
        "Europe/Kyiv", LocalTime.of(15, 0), LocalTime.of(18, 0), insideDayWindow));

    Instant insideOvernightWindow = Instant.parse("2026-09-24T22:30:00Z");
    assertEquals(Instant.parse("2026-09-25T04:00:00Z"), TaskReminderService.quietHoursEnd(
        "Europe/Kyiv", LocalTime.of(23, 0), LocalTime.of(7, 0), insideOvernightWindow));
  }

  @Test
  void pushEndpointsMustUseSupportedProviderHostsOverHttps() {
    assertTrue(TaskReminderService.safePushEndpoint(URI.create("https://fcm.googleapis.com/send/example")));
    assertTrue(TaskReminderService.safePushEndpoint(URI.create("https://web.push.apple.com/example")));
    assertFalse(TaskReminderService.safePushEndpoint(URI.create("http://fcm.googleapis.com/send/example")));
    assertFalse(TaskReminderService.safePushEndpoint(URI.create("https://localhost/admin")));
    assertFalse(TaskReminderService.safePushEndpoint(URI.create("https://attacker.example/push")));
  }

  @Test
  void taskAndOperationalPayloadsContainOnlyGenericTextAndOpaqueTokens() throws Exception {
    UUID token = UUID.fromString("103c6e4c-605e-49d8-9db3-45e97f5fdff0");
    var mapper = new ObjectMapper();
    var task = mapper.writeValueAsString(TaskReminderService.taskPushPayload(token));
    assertTrue(task.contains("Care task reminder"));
    assertTrue(task.contains("/app/tasks?reminder=" + token));
    assertFalse(task.contains("patient"));
    assertFalse(task.contains("diagnosis"));

    var operational = mapper.writeValueAsString(TaskReminderService.operationalPushPayload(token));
    assertTrue(operational.contains("Department alert"));
    assertTrue(operational.contains("/app/dashboard?notification=" + token));
    assertFalse(operational.contains("patient"));
    assertFalse(operational.contains("diagnosis"));
  }
}
