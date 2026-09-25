package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!",
    "app.patient-consent.portal-summary-version=2"
})
@AutoConfigureMockMvc
class PatientConsentIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void legacyConsentCannotPublishOrExposePatientSummaryUnderCurrentPolicy() throws Exception {
    long patientId = createPatient();
    String patientUsername = createPatientAccount(patientId);
    long adminId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    Workflow workflow = createWorkflow();

    jdbc.update("""
        insert into patient_consents(department_id,patient_id,consent_type,consent_version,recorded_by)
        values (1,?,'PORTAL_FOLLOW_UP_SUMMARY','1',?)
        """, patientId, adminId);

    MvcResult staleChoice = mvc.perform(post("/api/v1/portal/consents")
            .with(user(patientUsername).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("consentType", "PORTAL_FOLLOW_UP_SUMMARY",
                "consentVersion", "1", "confirmed", true))))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(staleChoice.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("CONSENT_VERSION_STALE");

    String launchKey = "stale-consent-" + UUID.randomUUID();
    MvcResult rejectedLaunch = mvc.perform(adminPost("/api/v1/care-workflows/" + workflow.templateId() + "/launch",
            Map.of("workflowVersion", workflow.version(), "patientId", patientId,
                "idempotencyKey", launchKey, "approved", true,
                "patientSummary", "New follow-up summary requiring current consent.")))
        .andExpect(status().isForbidden()).andReturn();
    assertThat(json.readTree(rejectedLaunch.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("PORTAL_CONSENT_REQUIRED");
    assertThat(jdbc.queryForObject("select count(*) from care_workflow_runs where trigger_source_id=?",
        Integer.class, launchKey)).isZero();

    jdbc.update("""
        insert into care_workflow_runs(department_id,template_id,workflow_version_id,patient_id,
          trigger_type,trigger_source_id,status,patient_summary,triggered_by,reviewed_by,reviewed_at,launched_by,launched_at)
        values (1,?,?,?,'MANUAL',?,'ACTIVE',?,?,?,now(),?,now())
        """, workflow.templateId(), workflow.versionId(), patientId,
        "legacy-summary-" + UUID.randomUUID(), "Previously approved summary", adminId, adminId, adminId);

    JsonNode summaries = patientGet("/api/v1/portal/care-summaries", patientUsername);
    assertThat(summaries.size()).isZero();
  }

  @Test
  void withdrawingCurrentConsentHidesPreviouslyVisibleSummary() throws Exception {
    long patientId = createPatient();
    String patientUsername = createPatientAccount(patientId);
    Workflow workflow = createWorkflow();

    JsonNode consent = patientPost("/api/v1/portal/consents", Map.of(
        "consentType", "PORTAL_FOLLOW_UP_SUMMARY", "consentVersion", "2", "confirmed", true), patientUsername);
    assertThat(consent.path("consentVersion").asText()).isEqualTo("2");

    mvc.perform(adminPost("/api/v1/care-workflows/" + workflow.templateId() + "/launch", Map.of(
            "workflowVersion", workflow.version(), "patientId", patientId,
            "idempotencyKey", "current-consent-" + UUID.randomUUID(), "approved", true,
            "patientSummary", "Call cardiology to arrange a follow-up.")))
        .andExpect(status().isCreated());

    JsonNode visible = patientGet("/api/v1/portal/care-summaries", patientUsername);
    assertThat(visible.size()).isEqualTo(1);
    assertThat(visible.get(0).path("summary").asText()).isEqualTo("Call cardiology to arrange a follow-up.");

    JsonNode withdrawn = patientPost("/api/v1/portal/consents/" + consent.path("id").asLong() + "/withdraw",
        Map.of("confirmed", true), patientUsername);
    assertThat(withdrawn.path("withdrawnAt").isNull()).isFalse();
    assertThat(patientGet("/api/v1/portal/care-summaries", patientUsername).size()).isZero();
  }

  private long createPatient() throws Exception {
    String tag = UUID.randomUUID().toString();
    MvcResult result = mvc.perform(adminPost("/api/v1/patients", Map.of(
            "patientIdentifier", "CONSENT-" + tag,
            "firstName", "Consent",
            "lastName", "Patient",
            "dateOfBirth", "1983-02-01")))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private String createPatientAccount(long patientId) {
    String username = "consent_patient_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.queryForObject("""
        insert into app_users(username,password_hash,role,enabled,email_verified,requested_role,patient_id)
        values (?, 'unused-test-password-hash', 'PATIENT', true, true, 'PATIENT', ?) returning id
        """, Long.class, username, patientId);
    return username;
  }

  private Workflow createWorkflow() throws Exception {
    String key = UUID.randomUUID().toString();
    Map<String, Object> task = Map.of("key", "review", "title", "Review follow-up", "ownerRole", "ADMIN",
        "dueOffsetMinutes", 0, "dependsOn", List.of());
    JsonNode template = json.readTree(mvc.perform(adminPost("/api/v1/care-workflows", Map.of(
            "name", "Consent workflow " + key, "description", "", "triggers", List.of("MANUAL"),
            "tasks", List.of(task), "version", 0)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    JsonNode published = json.readTree(mvc.perform(adminPost("/api/v1/care-workflows/"
            + template.path("id").asLong() + "/publish", Map.of("version", 0)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    return new Workflow(template.path("id").asLong(), published.path("version").asLong(),
        published.path("workflowVersionId").asLong());
  }

  private JsonNode patientGet(String path, String username) throws Exception {
    MvcResult result = mvc.perform(get(path).with(user(username).roles("PATIENT")).with(csrf()))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode patientPost(String path, Object body, String username) throws Exception {
    MvcResult result = mvc.perform(post(path).with(user(username).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is2xxSuccessful()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder adminPost(String path, Object body) throws Exception {
    return post(path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }

  private record Workflow(long templateId, long version, long versionId) {}
}
