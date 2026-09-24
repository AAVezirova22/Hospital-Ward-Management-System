package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.seed=true", "app.bootstrap-password=IntegrationPassword123!"})
@AutoConfigureMockMvc
class CareWorkflowIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private JsonNode post(String path, Object body) throws Exception {
    var result = mvc.perform(post("/api/v1" + path).with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andExpect(status().is2xxSuccessful()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode patch(String path, Object body) throws Exception {
    var result = mvc.perform(patch("/api/v1" + path).with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void publishesImmutableVersionAndCreatesTasksWithDependencyLifecycle() throws Exception {
    Long patientId = jdbc.queryForObject("select min(id) from patients where department_id=1", Long.class);
    var taskA = Map.of("key", "follow-up", "title", "Clinician-authored follow-up", "ownerRole", "MEDICAL_STAFF",
        "dueOffsetMinutes", 0, "dependsOn", List.of());
    var taskB = Map.of("key", "review", "title", "Review the follow-up", "ownerRole", "DOCTOR",
        "dueOffsetMinutes", 120, "dependsOn", List.of("follow-up"));
    var created = post("/care-workflows", Map.of("name", "Care plan " + UUID.randomUUID(), "description", "",
        "triggers", List.of("MANUAL"), "tasks", List.of(taskA, taskB), "version", 0));
    long templateId = created.get("id").asLong();

    var published = post("/care-workflows/" + templateId + "/publish", Map.of("version", 0));
    assertThat(published.get("version").asInt()).isEqualTo(1);
    var preview = post("/care-workflows/" + templateId + "/preview", Map.of("patientId", patientId, "trigger", "MANUAL"));
    assertThat(preview.get("tasks").get(0).get("dueAt").asText()).isEqualTo(preview.get("baseTime").asText());
    var launched = post("/care-workflows/" + templateId + "/launch", Map.of(
        "workflowVersion", published.get("version").asLong(), "patientId", patientId,
        "idempotencyKey", "care-workflow-test", "patientSummary", "", "approved", true));
    long runId = launched.get("id").asLong();
    assertThat(launched.get("tasks").size()).isEqualTo(2);
    assertThat(launched.get("tasks").get(1).get("dependencyState").asText()).isEqualTo("BLOCKED");

    var tasks = mvc.perform(get("/api/v1/care-tasks").with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    JsonNode taskList = json.readTree(tasks.getResponse().getContentAsString());
    JsonNode first = taskList.findValues("workflowRunId").isEmpty() ? null : taskList.get(0);
    assertThat(first).isNotNull();
    assertThat(first.get("patientId").asLong()).isEqualTo(patientId);
    long taskId = first.get("id").asLong();
    patch("/care-tasks/" + taskId, Map.of("status", "COMPLETED", "version", 0));
    var run = mvc.perform(get("/api/v1/care-workflow-runs/" + runId).with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    JsonNode runJson = json.readTree(run.getResponse().getContentAsString());
    assertThat(runJson.get("tasks").get(1).get("dependencyState").asText()).isEqualTo("READY");

    Long userId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    Long pendingRunId = jdbc.queryForObject("""
        insert into care_workflow_runs(department_id,template_id,workflow_version_id,patient_id,
          trigger_type,trigger_source_id,status,triggered_by)
        values (1,?,?,?,'ADMISSION','admission:test-trigger','PENDING_REVIEW',?) returning id
        """, Long.class, templateId, published.get("workflowVersionId").asLong(), patientId, userId);
    assertThat(jdbc.queryForObject("select count(*) from care_tasks where workflow_run_id=?", Integer.class, pendingRunId)).isZero();
    var approved = post("/care-workflow-runs/" + pendingRunId + "/approve", Map.of("approved", true));
    assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(approved.get("tasks").size()).isEqualTo(2);
  }
}
