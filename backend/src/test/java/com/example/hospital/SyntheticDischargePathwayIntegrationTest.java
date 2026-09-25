package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.ai.AiModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end coverage for clinician-reviewed discharge documents becoming consent-gated follow-up tasks. */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!",
    "app.ai.mode=external",
    "app.ai.url=https://provider.example/v1/chat/completions",
    "app.ai.model=synthetic-discharge-fixture",
    "app.ai.rate-limit=10000"
})
@AutoConfigureMockMvc
class SyntheticDischargePathwayIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean AiModelClient model;

  @Test
  void reviewedDischargeDocumentFlowsThroughVersionedPathwayConsentReminderAndCancellation() throws Exception {
    String patientIdentifier = "SYNTH-DISCHARGE-" + UUID.randomUUID();
    LocalDate followUpDate = LocalDate.now().plusDays(30);
    String followUpDateText = followUpDate.toString();
    String actionExcerpt = "Follow up with cardiology by " + followUpDateText + " at 09:30";
    String sourceText = """
        SYNTHETIC DISCHARGE SUMMARY
        Patient ID: %s
        Name: Eleni Markou
        Date of birth: 1980-04-03
        Phone: +359888123456
        %s
        """.formatted(patientIdentifier, actionExcerpt);
    String draftJson = """
        {"patientIdentifier":[{"value":"%s","confidence":0.98,"excerpt":"%s","location":"page 1"}],
         "firstName":[{"value":"Eleni","confidence":0.94,"excerpt":"Eleni Markou","location":"page 1"}],
         "lastName":[{"value":"Markou","confidence":0.94,"excerpt":"Eleni Markou","location":"page 1"}],
         "dateOfBirth":[{"value":"1980-04-03","confidence":0.9,"excerpt":"1980-04-03","location":"page 1"}],
         "address":[],
         "phoneNumber":[{"value":"+359888123456","confidence":0.91,"excerpt":"+359888123456","location":"page 1"}],
         "followUpActions":[{"title":"Follow up with cardiology","dueDate":"%s","dueTime":"09:30",
           "confidence":0.92,"excerpt":"%s","location":"page 1","conflicts":[]}]}
        """.formatted(patientIdentifier, patientIdentifier, followUpDateText, actionExcerpt);
    org.mockito.Mockito.when(model.identifier()).thenReturn("synthetic-discharge-fixture");
    org.mockito.Mockito.when(model.complete(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any())).thenReturn(
            new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", draftJson)));

    MvcResult uploaded = mvc.perform(multipart("/api/v1/assistant/sources")
            .file(new MockMultipartFile("file", "synthetic-discharge-summary.txt", "text/plain",
                sourceText.getBytes(StandardCharsets.UTF_8)))
            .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(uploaded.getResponse().getContentAsString()).path("id").asText();
    assertThat(sourceId).isNotBlank();

    JsonNode draft = adminPost("/assistant/patient-drafts", Map.of("sourceId", sourceId), 200);
    assertThat(draft.path("reviewRequired").asBoolean()).isTrue();
    assertThat(draft.path("saved").asBoolean()).isFalse();
    assertThat(draft.at("/fields/phoneNumber/proposedValue").asText()).isEqualTo("+359888123456");
    assertThat(draft.at("/followUpActions/0/sources/0/excerpt").asText()).isEqualTo(actionExcerpt);
    String draftId = draft.path("draftId").asText();
    String actionId = draft.at("/followUpActions/0/actionId").asText();

    // Saving is an explicit clinician decision using reviewed draft values; extraction itself creates no patient.
    JsonNode patient = adminPost("/patients", Map.of(
        "patientIdentifier", draft.at("/fields/patientIdentifier/proposedValue").asText(),
        "firstName", draft.at("/fields/firstName/proposedValue").asText(),
        "lastName", draft.at("/fields/lastName/proposedValue").asText(),
        "dateOfBirth", draft.at("/fields/dateOfBirth/proposedValue").asText(),
        "phoneNumber", draft.at("/fields/phoneNumber/proposedValue").asText()), 201);
    long patientId = patient.path("id").asLong();
    JsonNode boundDraft = adminPut("/assistant/patient-drafts/" + draftId + "/patient",
        Map.of("patientId", patientId), 200);
    assertThat(boundDraft.path("patientId").asLong()).isEqualTo(patientId);
    assertThat(jdbc.queryForObject("select phone_number from patients where department_id=1 and id=?",
        String.class, patientId)).isEqualTo("+359888123456");
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='PATIENT_DRAFT_EXTRACTED'",
        Integer.class)).isGreaterThan(0);

    long adminId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    String templateName = "Synthetic discharge pathway " + UUID.randomUUID();
    Map<String, Object> initialTask = Map.of("key", "review-discharge", "title", "Review discharge plan",
        "ownerRole", "ADMIN", "dueOffsetMinutes", 60, "dependsOn", List.of());
    JsonNode template = adminPost("/care-workflows", Map.of("name", templateName, "description", "",
        "triggers", List.of("MANUAL", "DISCHARGE"), "tasks", List.of(initialTask), "version", 0), 201);
    long templateId = template.path("id").asLong();
    JsonNode firstVersion = adminPost("/care-workflows/" + templateId + "/publish", Map.of("version", 0), 200);
    assertThat(firstVersion.path("version").asLong()).isEqualTo(1);

    Map<String, Object> editedTask = Map.of("key", "review-discharge", "title", "Review discharge instructions",
        "ownerRole", "ADMIN", "dueOffsetMinutes", 60, "dependsOn", List.of());
    adminPut("/care-workflows/" + templateId, Map.of("name", templateName, "description", "",
        "triggers", List.of("MANUAL", "DISCHARGE"), "tasks", List.of(editedTask), "version", 1), 200);
    JsonNode secondVersion = adminPost("/care-workflows/" + templateId + "/publish", Map.of("version", 2), 200);
    assertThat(secondVersion.path("version").asLong()).isEqualTo(2);
    String storedFirstDefinition = jdbc.queryForObject(
        "select definition from care_workflow_versions where department_id=1 and template_id=? and version_number=1",
        String.class, templateId);
    assertThat(json.readTree(storedFirstDefinition).at("/tasks/0/title").asText()).isEqualTo("Review discharge plan");
    assertThat(secondVersion.at("/definition/tasks/0/title").asText()).isEqualTo("Review discharge instructions");

    List<Map<String, Object>> reviewedActions = List.of(Map.of(
        "actionId", actionId,
        "title", "Arrange cardiology follow-up",
        "dueDate", followUpDateText,
        "dueTime", "09:30",
        "decision", "EDITED",
        "ownerRole", "ADMIN",
        "assignedUserId", adminId,
        "dependsOn", List.of()));
    Map<String, Object> previewInput = Map.of("patientId", patientId, "trigger", "MANUAL",
        "workflowVersion", secondVersion.path("version").asLong(), "patientDraftId", draftId,
        "reviewedFollowUpActions", reviewedActions);
    JsonNode preview = adminPost("/care-workflows/" + templateId + "/preview", previewInput, 200);
    JsonNode previewDocumentTask = taskWithOrigin(preview.path("tasks"), "DOCUMENT");
    assertThat(preview.at("/tasks/0/title").asText()).isEqualTo("Review discharge instructions");
    assertThat(previewDocumentTask.path("title").asText()).isEqualTo("Arrange cardiology follow-up");
    assertThat(previewDocumentTask.path("sourceExcerpt").asText()).isEqualTo(actionExcerpt);
    assertThat(previewDocumentTask.path("sourceEdited").asBoolean()).isTrue();
    assertThat(previewDocumentTask.path("assignedUserId").asLong()).isEqualTo(adminId);

    String patientUsername = createPatientAccount(patientId);
    assertThat(patientGet("/portal/care-summaries", patientUsername).isEmpty()).isTrue();
    String launchKey = "synthetic-discharge-" + UUID.randomUUID();
    Map<String, Object> launchInput = new java.util.LinkedHashMap<>();
    launchInput.put("workflowVersion", secondVersion.path("version").asLong());
    launchInput.put("patientId", patientId);
    launchInput.put("idempotencyKey", launchKey);
    launchInput.put("patientSummary", "Clinician-approved cardiology follow-up is planned.");
    launchInput.put("approved", true);
    launchInput.put("patientDraftId", draftId);
    launchInput.put("reviewedFollowUpActions", reviewedActions);
    MvcResult rejectedLaunch = mvc.perform(adminPostBuilder("/care-workflows/" + templateId + "/launch", launchInput))
        .andExpect(status().isForbidden()).andReturn();
    assertThat(json.readTree(rejectedLaunch.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("PORTAL_CONSENT_REQUIRED");
    assertThat(jdbc.queryForObject("select count(*) from care_workflow_runs where trigger_source_id=?",
        Integer.class, launchKey)).isZero();

    JsonNode options = patientGet("/portal/consent-options", patientUsername);
    String summaryConsentVersion = "";
    for (JsonNode option : options) {
      if ("PORTAL_FOLLOW_UP_SUMMARY".equals(option.path("consentType").asText()))
        summaryConsentVersion = option.path("consentVersion").asText();
    }
    assertThat(summaryConsentVersion).isNotBlank();
    JsonNode consent = patientPost("/portal/consents", Map.of(
        "consentType", "PORTAL_FOLLOW_UP_SUMMARY",
        "consentVersion", summaryConsentVersion,
        "confirmed", true), patientUsername);
    assertThat(consent.path("consentVersion").asText()).isEqualTo(summaryConsentVersion);
    assertThat(patientGet("/portal/care-summaries", patientUsername).isEmpty()).isTrue();

    String pushEndpoint = "https://fcm.googleapis.com/send/" + UUID.randomUUID();
    long subscriptionId = jdbc.queryForObject(
        "insert into push_subscriptions(user_id, endpoint, p256dh_key, auth_secret) values (?, ?, 'test-key', 'test-auth') returning id",
        Long.class, adminId, pushEndpoint);
    Map<String, Object> reminderPreferences = new java.util.LinkedHashMap<>();
    reminderPreferences.put("optedIn", true);
    reminderPreferences.put("timeZone", "UTC");
    reminderPreferences.put("minutesBefore", 0);
    reminderPreferences.put("quietHoursStart", null);
    reminderPreferences.put("quietHoursEnd", null);
    reminderPreferences.put("operationalAlerts", false);
    adminPut("/task-reminders/preferences", reminderPreferences, 200);

    JsonNode launched = adminPost("/care-workflows/" + templateId + "/launch", launchInput, 201);
    long runId = launched.path("id").asLong();
    assertThat(launched.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(launched.path("workflowVersion").asLong()).isEqualTo(2);
    assertThat(launched.path("sourceReference").asText()).isEqualTo(sourceId);
    JsonNode documentTask = taskWithOrigin(launched.path("tasks"), "DOCUMENT");
    long taskId = documentTask.path("id").asLong();
    assertThat(documentTask.path("title").asText()).isEqualTo("Arrange cardiology follow-up");
    assertThat(documentTask.path("reviewDecision").asText()).isEqualTo("EDITED");
    assertThat(documentTask.path("sourceEdited").asBoolean()).isTrue();
    assertThat(documentTask.path("sourceReference").asText()).isEqualTo(sourceId);
    assertThat(documentTask.path("sourceName").asText()).isEqualTo("synthetic-discharge-summary.txt");
    assertThat(documentTask.path("sourceExcerpt").asText()).isEqualTo(actionExcerpt);
    assertThat(documentTask.path("assignedUserId").asLong()).isEqualTo(adminId);
    assertThat(documentTask.path("dueAt").asText()).isNotBlank();

    Map<String, Object> reminder = jdbc.queryForMap(
        "select status, action_token, due_at, scheduled_at, recipient_user_id from task_reminders where task_id=? and subscription_id=?",
        taskId, subscriptionId);
    assertThat(reminder.get("status")).isEqualTo("PENDING");
    assertThat(reminder.get("recipient_user_id")).isEqualTo(adminId);
    assertThat(reminder.get("action_token")).isInstanceOf(UUID.class);
    assertThat(reminder.get("scheduled_at")).isEqualTo(reminder.get("due_at"));

    JsonNode visibleSummary = patientGet("/portal/care-summaries", patientUsername);
    assertThat(visibleSummary.size()).isEqualTo(1);
    assertThat(visibleSummary.get(0).path("summary").asText())
        .isEqualTo("Clinician-approved cardiology follow-up is planned.");
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_WORKFLOW_LAUNCHED' and entity_id=?",
        Integer.class, runId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_TASK_SOURCE_REVIEWED' and entity_id=?",
        Integer.class, taskId)).isEqualTo(1);

    JsonNode cancelled = adminPost("/care-workflow-runs/" + runId + "/cancel",
        Map.of("version", launched.path("version").asLong()), 200);
    assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
    assertThat(cancelled.path("cancelledAt").isNull()).isFalse();
    JsonNode retainedHistory = adminGet("/care-workflow-runs/" + runId, 200);
    assertThat(retainedHistory.path("sourceReference").asText()).isEqualTo(sourceId);
    assertThat(taskWithOrigin(retainedHistory.path("tasks"), "DOCUMENT").path("status").asText())
        .isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("select count(*) from task_reminders where task_id=? and subscription_id=? and status='CANCELLED'",
        Integer.class, taskId, subscriptionId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_TASK_CANCELLED' and entity_id=?",
        Integer.class, taskId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='CARE_WORKFLOW_CANCELLED' and entity_id=?",
        Integer.class, runId)).isEqualTo(1);
    assertThat(patientGet("/portal/care-summaries", patientUsername).isEmpty()).isTrue();
  }

  private JsonNode adminPost(String path, Object body, int expectedStatus) throws Exception {
    return read(adminPostBuilder(path, body), expectedStatus);
  }

  private JsonNode adminPut(String path, Object body, int expectedStatus) throws Exception {
    return read(put("/api/v1" + path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), expectedStatus);
  }

  private JsonNode adminGet(String path, int expectedStatus) throws Exception {
    return read(get("/api/v1" + path).with(user("admin")).with(csrf()).header("X-Department-Id", "1"), expectedStatus);
  }

  private MockHttpServletRequestBuilder adminPostBuilder(String path, Object body) throws Exception {
    return post("/api/v1" + path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }

  private JsonNode read(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
    ResultActions response = mvc.perform(request).andExpect(status().is(expectedStatus));
    return json.readTree(response.andReturn().getResponse().getContentAsString());
  }

  private JsonNode patientGet(String path, String username) throws Exception {
    MvcResult response = mvc.perform(get("/api/v1" + path).with(user(username).roles("PATIENT")).with(csrf()))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(response.getResponse().getContentAsString());
  }

  private JsonNode patientPost(String path, Object body, String username) throws Exception {
    MvcResult response = mvc.perform(post("/api/v1" + path).with(user(username).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(response.getResponse().getContentAsString());
  }

  private String createPatientAccount(long patientId) {
    String username = "synthetic_patient_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.queryForObject("""
        insert into app_users(username,password_hash,role,enabled,email_verified,requested_role,patient_id)
        values (?, 'unused-test-password-hash', 'PATIENT', true, true, 'PATIENT', ?) returning id
        """, Long.class, username, patientId);
    return username;
  }

  private static JsonNode taskWithOrigin(JsonNode tasks, String origin) {
    for (JsonNode task : tasks) if (origin.equals(task.path("taskOrigin").asText())) return task;
    throw new AssertionError("No task with origin " + origin + " was returned.");
  }
}
