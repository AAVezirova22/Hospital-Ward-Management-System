package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.CareWorkflowService;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = {"app.seed=true", "app.bootstrap-password=IntegrationPassword123!"})
@AutoConfigureMockMvc
class CareWorkflowIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired CareWorkflowService workflows;

  private JsonNode post(String path, Object body) throws Exception {
    var result = mvc.perform(MockMvcRequestBuilders.post("/api/v1" + path).with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andExpect(status().is2xxSuccessful()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode patch(String path, Object body) throws Exception {
    var result = mvc.perform(MockMvcRequestBuilders.patch("/api/v1" + path).with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode put(String path, Object body) throws Exception {
    var result = mvc.perform(MockMvcRequestBuilders.put("/api/v1" + path).with(user("admin")).with(csrf())
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
    assertThat(launched.get("reviewedBy").asLong()).isGreaterThan(0);
    assertThat(launched.get("reviewedAt").isNull()).isFalse();
    assertThat(launched.get("launchedAt").isNull()).isFalse();
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
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_TASK_CREATED' and entity_id in (select id from care_tasks where workflow_run_id=?)", Integer.class, runId)).isEqualTo(2);

    Long userId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    Long pendingRunId = jdbc.queryForObject("""
        insert into care_workflow_runs(department_id,template_id,workflow_version_id,patient_id,
          trigger_type,trigger_source_id,status,triggered_by)
        values (1,?,?,?,'ADMISSION','admission:test-trigger','PENDING_REVIEW',?) returning id
        """, Long.class, templateId, published.get("workflowVersionId").asLong(), patientId, userId);
    assertThat(jdbc.queryForObject("select count(*) from care_tasks where workflow_run_id=?", Integer.class, pendingRunId)).isZero();
    var approved = post("/care-workflow-runs/" + pendingRunId + "/approve", Map.of("approved", true));
    assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(approved.get("reviewedAt").isNull()).isFalse();
    assertThat(approved.get("launchedAt").isNull()).isFalse();
    assertThat(approved.get("tasks").size()).isEqualTo(2);
  }

  @Test
  void launchOverrideCanClearAssigneeWhenOwnerRoleChanges() throws Exception {
    Long patientId = jdbc.queryForObject("select min(id) from patients where department_id=1", Long.class);
    Long adminId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    var task = Map.of("key", "handoff", "title", "Review handoff", "ownerRole", "ADMIN",
        "assignedUserId", adminId, "dueOffsetMinutes", 0, "dependsOn", List.of());
    var created = post("/care-workflows", Map.of("name", "Clear owner " + UUID.randomUUID(), "description", "",
        "triggers", List.of("MANUAL"), "tasks", List.of(task), "version", 0));
    long templateId = created.get("id").asLong();
    var published = post("/care-workflows/" + templateId + "/publish", Map.of("version", 0));

    var override = new java.util.LinkedHashMap<String, Object>();
    override.put("key", "handoff");
    override.put("ownerRole", "DOCTOR");
    override.put("assignedUserId", null);
    var overrides = List.of(override);
    var preview = post("/care-workflows/" + templateId + "/preview", Map.of(
        "patientId", patientId, "trigger", "MANUAL", "workflowVersion", published.get("version").asLong(),
        "taskOverrides", overrides));
    assertThat(preview.at("/tasks/0/ownerRole").asText()).isEqualTo("DOCTOR");
    assertThat(preview.at("/tasks/0/assignedUserId").isNull()).isTrue();

    var launched = post("/care-workflows/" + templateId + "/launch", Map.of(
        "workflowVersion", published.get("version").asLong(), "patientId", patientId,
        "idempotencyKey", UUID.randomUUID().toString(), "approved", true, "taskOverrides", overrides));
    long runId = launched.get("id").asLong();
    assertThat(jdbc.queryForObject("select assigned_user_id is null from care_tasks where workflow_run_id=?",
        Boolean.class, runId)).isTrue();
  }

  @Test
  void retriedAdmissionTriggerAfterRepublishReusesOriginalRun() throws Exception {
    String patientTag = UUID.randomUUID().toString();
    Long patientId = post("/patients", Map.of("patientIdentifier", "TRIGGER-PATIENT-" + patientTag,
        "firstName", "Trigger", "lastName", "Retry", "dateOfBirth", "1980-01-01")).get("id").asLong();
    Long doctorId = jdbc.queryForObject("select min(id) from doctors where department_id=1 and active=true", Long.class);
    Long adminId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    Long admissionId = jdbc.queryForObject("""
        insert into admissions(department_id,admission_number,patient_id,attending_doctor_id,admission_date_time,status,created_by)
        values (1,?,?,?,now(),'ACTIVE',?) returning id
        """, Long.class, "TRIGGER-" + UUID.randomUUID(), patientId, doctorId, adminId);
    var task = Map.of("key", "review", "title", "Review the case", "ownerRole", "DOCTOR",
        "dueOffsetMinutes", 0, "dependsOn", List.of());
    var workflowBody = new java.util.LinkedHashMap<String, Object>();
    workflowBody.put("name", "Trigger retry " + UUID.randomUUID());
    workflowBody.put("description", ""); workflowBody.put("triggers", List.of("ADMISSION"));
    workflowBody.put("tasks", List.of(task)); workflowBody.put("version", 0);
    long templateId = post("/care-workflows", workflowBody).get("id").asLong();
    var firstVersion = post("/care-workflows/" + templateId + "/publish", Map.of("version", 0));

    var auth = new UsernamePasswordAuthenticationToken("admin", "",
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    DepartmentContext.set(new DepartmentContext.Scope(1L, "ADMIN", null));
    try {
      workflows.launchForTrigger(admissionId, "ADMISSION");
    } finally {
      DepartmentContext.clear();
      SecurityContextHolder.clearContext();
    }
    Long originalRunId = jdbc.queryForObject("""
        select id from care_workflow_runs where department_id=1 and template_id=? and trigger_type='ADMISSION'
          and trigger_source_id=?
        """, Long.class, templateId, "admission:" + admissionId);
    Long originalVersionId = jdbc.queryForObject("select workflow_version_id from care_workflow_runs where id=?", Long.class, originalRunId);
    assertThat(originalVersionId).isEqualTo(firstVersion.get("workflowVersionId").asLong());

    workflowBody.put("version", 1);
    workflowBody.put("tasks", List.of(Map.of("key", "review", "title", "Review the updated case",
        "ownerRole", "DOCTOR", "dueOffsetMinutes", 0, "dependsOn", List.of())));
    put("/care-workflows/" + templateId, workflowBody);
    var secondVersion = post("/care-workflows/" + templateId + "/publish", Map.of("version", 2));
    assertThat(secondVersion.get("version").asInt()).isEqualTo(2);

    SecurityContextHolder.getContext().setAuthentication(auth);
    DepartmentContext.set(new DepartmentContext.Scope(1L, "ADMIN", null));
    try {
      workflows.launchForTrigger(admissionId, "ADMISSION");
    } finally {
      DepartmentContext.clear();
      SecurityContextHolder.clearContext();
    }
    assertThat(jdbc.queryForObject("""
        select count(*) from care_workflow_runs where department_id=1 and template_id=? and trigger_type='ADMISSION'
          and trigger_source_id=?
        """, Integer.class, templateId, "admission:" + admissionId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select workflow_version_id from care_workflow_runs where id=?", Long.class, originalRunId))
        .isEqualTo(originalVersionId);
  }

  @Test
  void cancellingRunRetainsTasksAndClosesReminderEligibleWork() throws Exception {
    Long patientId = jdbc.queryForObject("select min(id) from patients where department_id=1", Long.class);
    var task = Map.of("key", "open-task", "title", "A trackable follow-up", "ownerRole", "MEDICAL_STAFF",
        "dueOffsetMinutes", 30, "dependsOn", List.of());
    var created = post("/care-workflows", Map.of("name", "Cancel " + UUID.randomUUID(), "description", "",
        "triggers", List.of("MANUAL"), "tasks", List.of(task), "version", 0));
    long templateId = created.get("id").asLong();
    var published = post("/care-workflows/" + templateId + "/publish", Map.of("version", 0));
    var launched = post("/care-workflows/" + templateId + "/launch", Map.of(
        "workflowVersion", published.get("version").asLong(), "patientId", patientId,
        "idempotencyKey", UUID.randomUUID().toString(), "approved", true));
    long runId = launched.get("id").asLong();
    long taskId = launched.at("/tasks/0/id").asLong();
    var cancelled = post("/care-workflow-runs/" + runId + "/cancel", Map.of("version", launched.get("version").asLong()));
    assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(cancelled.at("/tasks/0/status").asText()).isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("select count(*) from care_tasks where id=?", Integer.class, taskId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_TASK_CANCELLED' and entity_id=?", Integer.class, taskId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_WORKFLOW_CANCELLED' and entity_id=?", Integer.class, runId)).isEqualTo(1);
  }
}
