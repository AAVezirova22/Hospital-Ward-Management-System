package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.retention.audit-days=30", "app.retention.ai-interaction-days=30"})
class RetentionTest extends HospitalSupport {
  private long oldAuditEvent(long departmentId) {
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    return jdbc.queryForObject(
        "insert into audit_events(department_id, user_id, event_type, entity_type, source, timestamp, metadata)"
            + " values (?, ?, 'PATIENT_CREATED', 'Patient', 'UI', ?, '{}') returning id",
        Long.class,
        departmentId,
        adminId,
        Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)));
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private JsonNode category(JsonNode report, String name) {
    for (var row : report.get("categories")) if (row.get("category").asText().equals(name)) return row;
    throw new AssertionError(name);
  }

  @Test
  void previewCountsWithoutDeletingAndApplyRemovesOnlyOldRowsOfThisDepartment() throws Exception {
    long old = oldAuditEvent(1);
    long otherDepartment =
        result(request("admin", "POST", "/api/v1/workspaces/hospitals",
                Map.of("name", "Retention " + unique(), "departmentName", "Archive")), 201)
            .path("departmentId").asLong();
    long otherOld = oldAuditEvent(otherDepartment);
    long recent = count("select max(id) from audit_events where department_id = 1");
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    jdbc.update(
        "insert into ai_interactions(department_id, user_id, session_id, started_at, status) values (1, ?, 'old', ?, 'COMPLETED')",
        adminId, Timestamp.from(Instant.now().minus(45, ChronoUnit.DAYS)));

    var preview = result(request("admin", "GET", "/api/v1/retention/preview", null), 200);
    assertThat(preview.get("dryRun").asBoolean()).isTrue();
    assertThat(category(preview, "AUDIT_EVENTS").get("eligible").asLong()).isPositive();
    assertThat(category(preview, "AI_INTERACTIONS").get("eligible").asLong()).isPositive();
    assertThat(count("select count(*) from audit_events where id = ?", old)).isOne();

    request("admin", "POST", "/api/v1/retention/apply", Map.of("confirmation", "yes"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));

    var applied =
        result(request("admin", "POST", "/api/v1/retention/apply", Map.of("confirmation", "APPLY RETENTION")), 200);
    assertThat(applied.get("dryRun").asBoolean()).isFalse();
    assertThat(category(applied, "AUDIT_EVENTS").get("removed").asLong()).isPositive();
    assertThat(count("select count(*) from audit_events where id = ?", old)).isZero();
    assertThat(count("select count(*) from audit_events where id = ?", recent)).isOne();
    assertThat(count("select count(*) from audit_events where id = ?", otherOld)).isOne();
    assertThat(count("select count(*) from ai_interactions where session_id = 'old' and department_id = 1")).isZero();
    assertThat(count("select count(*) from audit_events where event_type = 'RETENTION_APPLIED'")).isPositive();

    assertThatThrownBy(() -> jdbc.update("delete from audit_events where id = ?", otherOld))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("append-only");
  }

  @Test
  void onlyAdministratorsManageRetention() throws Exception {
    request("staff", "GET", "/api/v1/retention/preview", null).andExpect(status().isForbidden());
    request("staff", "POST", "/api/v1/retention/apply", Map.of("confirmation", "APPLY RETENTION"))
        .andExpect(status().isForbidden());
  }
}
