package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AssistantDataTest extends HospitalSupport {
  private long pendingProposal(String username) {
    long userId = users.findByUsername(username).orElseThrow().getId();
    return jdbc.queryForObject(
        "insert into ai_pending_actions(department_id, user_id, action_type, payload, status, expires_at)"
            + " values (1, ?, 'ADMIT', '{}', 'PENDING', ?) returning id",
        Long.class, userId, Timestamp.from(Instant.now().plusSeconds(300)));
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  @Test
  void usersSeeAndClearOnlyTheirOwnAssistantData() throws Exception {
    result(request("admin", "POST", "/api/v1/assistant/messages", Map.of("message", "status")), 200);
    result(request("staff", "POST", "/api/v1/assistant/messages", Map.of("message", "status")), 200);
    long proposal = pendingProposal("admin");
    mvc.perform(multipart("/api/v1/assistant/sources")
            .file(new MockMultipartFile("file", "notes.txt", "text/plain", "Ward notes".getBytes(StandardCharsets.UTF_8)))
            .with(user("admin")).with(csrf()))
        .andExpect(status().isOk());

    var mine = result(request("admin", "GET", "/api/v1/assistant/my-data", null), 200);
    assertThat(mine.get("sessions").size()).isPositive();
    assertThat(mine.get("interactions").size()).isPositive();
    assertThat(mine.get("proposals").toString()).contains("\"id\":" + proposal);
    assertThat(mine.get("uploadedFiles").get(0).get("name").asText()).isEqualTo("notes.txt");
    assertThat(mine.get("uploadedFiles").toString()).doesNotContain("Ward notes");
    assertThat(mine.get("retainedAfterClearing").size()).isPositive();

    request("admin", "POST", "/api/v1/assistant/my-data/clear", Map.of("confirmation", "yes"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));

    long adminId = users.findByUsername("admin").orElseThrow().getId();
    long staffId = users.findByUsername("staff").orElseThrow().getId();
    long staffSessions = count("select count(*) from ai_sessions where user_id = ?", staffId);
    long aiAuditBefore = count("select count(*) from audit_events where user_id = ? and event_type like 'AI\\_%'", adminId);

    var cleared = result(request("admin", "POST", "/api/v1/assistant/my-data/clear",
        Map.of("confirmation", "CLEAR ASSISTANT DATA")), 200);
    assertThat(cleared.get("pendingProposalsWithdrawn").asLong()).isPositive();
    assertThat(cleared.get("uploadedFiles").asInt()).isOne();

    var after = result(request("admin", "GET", "/api/v1/assistant/my-data", null), 200);
    assertThat(after.get("sessions").size()).isZero();
    assertThat(after.get("interactions").size()).isZero();
    assertThat(after.get("proposals").size()).isZero();
    assertThat(after.get("uploadedFiles").size()).isZero();
    request("admin", "POST", "/api/v1/ai-actions/" + proposal + "/confirm", null).andExpect(status().isNotFound());

    assertThat(count("select count(*) from ai_sessions where user_id = ?", staffId)).isEqualTo(staffSessions);
    assertThat(count("select count(*) from audit_events where user_id = ? and event_type like 'AI\\_%'", adminId))
        .isEqualTo(aiAuditBefore);
    assertThat(count("select count(*) from audit_events where event_type = 'ASSISTANT_DATA_CLEARED' and entity_id = ?", adminId))
        .isPositive();
  }
}
