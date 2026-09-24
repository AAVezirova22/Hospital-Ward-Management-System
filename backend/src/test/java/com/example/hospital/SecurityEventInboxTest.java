package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityEventInboxTest extends HospitalSupport {
  private static final String INBOX = "/api/v1/security/events";
  private String username;
  private long userId;

  @BeforeEach
  void member() throws Exception {
    username = "sec-" + unique();
    var body = new HashMap<String, Object>();
    body.put("username", username);
    body.put("password", "SecurityPassword123!");
    body.put("role", "MEDICAL_STAFF");
    body.put("enabled", true);
    userId = result(request("admin", "POST", "/api/v1/users", body), 201).get("id").asLong();
  }

  private JsonNode find(JsonNode inbox, String type, String subject) {
    for (var event : inbox.get("events"))
      if (event.get("type").asText().equals(type) && subject.equals(event.path("subject").asText()))
        return event;
    return null;
  }

  private void failLogin(String name) throws Exception {
    mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", name).param("password", "not-the-password"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void failedSignInsAreGroupedPerAccountAndEscalate() throws Exception {
    failLogin(username);
    var quiet = find(result(request("admin", "GET", INBOX, null), 200), "LOGIN_FAILED", username);
    assertThat(quiet).as("a single failure stays below the default view").isNull();

    failLogin(username);
    failLogin(username);
    var event = find(result(request("admin", "GET", INBOX, null), 200), "LOGIN_FAILED", username);
    assertThat(event).isNotNull();
    assertThat(event.get("occurrences").asInt()).isEqualTo(3);
    assertThat(event.get("severity").asText()).isEqualTo("WARNING");
    assertThat(event.get("category").asText()).isEqualTo("FAILED_LOGIN");
    assertThat(event.toString()).doesNotContain("not-the-password");

    String unknown = "nobody-" + unique();
    failLogin(unknown);
    assertThat(find(result(request("admin", "GET", INBOX + "?includeInfo=true", null), 200), "LOGIN_FAILED", unknown))
        .isNull();
  }

  @Test
  void roleChangesAreQueuedAndTheInvestigationIsTracked() throws Exception {
    request("admin", "POST", "/api/v1/workspaces/departments/1/roles", Map.of("userId", userId, "role", "ADMIN"))
        .andExpect(status().isOk());
    var event = find(result(request("admin", "GET", INBOX, null), 200), "DEPARTMENT_ROLE_GRANTED", "admin");
    assertThat(event).isNotNull();
    assertThat(event.get("severity").asText()).isEqualTo("WARNING");
    long id = event.get("id").asLong();

    request("admin", "POST", INBOX + "/" + id + "/acknowledge", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledgedBy").value("admin"));
    request("admin", "PUT", INBOX + "/" + id, Map.of("status", "resolved", "note", "Planned promotion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.statusNote").value("Planned promotion"))
        .andExpect(jsonPath("$.statusChangedBy").value("admin"));
    assertThat(find(result(request("admin", "GET", INBOX + "?status=RESOLVED", null), 200), "DEPARTMENT_ROLE_GRANTED", "admin"))
        .isNotNull();

    request("admin", "POST", "/api/v1/workspaces/departments/1/roles", Map.of("userId", userId, "role", "MEDICAL_STAFF"))
        .andExpect(status().isOk());
    var reopened = find(result(request("admin", "GET", INBOX, null), 200), "DEPARTMENT_ROLE_GRANTED", "admin");
    assertThat(reopened.get("id").asLong()).isNotEqualTo(id);
    assertThat(reopened.get("occurrences").asInt()).isOne();
    assertThat(reopened.get("status").asText()).isEqualTo("OPEN");
  }

  @Test
  void onlyAdministratorsUseTheQueue() throws Exception {
    request("staff", "GET", INBOX, null).andExpect(status().isForbidden());
    request("admin", "GET", INBOX + "?status=MAYBE", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    request("admin", "PUT", INBOX + "/999999999", Map.of("status", "RESOLVED")).andExpect(status().isNotFound());
    var counts = result(request("admin", "GET", INBOX, null), 200).get("counts");
    assertThat(counts.has("open")).isTrue();
    assertThat(counts.has("critical")).isTrue();
  }
}
