package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.hospital.ai.AiModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.seed=true", "app.bootstrap-password=IntegrationPassword123!",
    "app.ai.mode=external", "app.ai.url=https://provider.example/v1/chat/completions",
    "app.ai.model=patient-draft-fixture", "app.ai.rate-limit=10000"})
@AutoConfigureMockMvc
class PatientDocumentDraftIntegrationTest {
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean AiModelClient model;
  private static final String TEXT = "Patient ID: P-DRAFT-427\nName: Alice Example\nDate of birth: 1981-04-03\nPhone: +359888123456\nFollow up with cardiology by 2026-10-12 at 09:30; other form says by 2026-10-13 at 10:00";

  @BeforeEach void setup() throws Exception {
    when(model.identifier()).thenReturn("patient-draft-fixture");
    String draft = """
        {"patientIdentifier":[{"value":"P-DRAFT-427","confidence":0.98,"excerpt":"P-DRAFT-427","location":"page 1"}],
         "firstName":[{"value":"Alice","confidence":0.94,"excerpt":"Alice Example","location":"page 1"}],
         "lastName":[{"value":"Example","confidence":0.94,"excerpt":"Alice Example","location":"page 1"}],
         "dateOfBirth":[{"value":"1981-04-03","confidence":0.9,"excerpt":"1981-04-03","location":"page 1"}],
         "address":[],"phoneNumber":[{"value":"+359888123456","confidence":0.91,"excerpt":"+359888123456","location":"page 1"}],
         "followUpActions":[{"title":"Follow up with cardiology","dueDate":"2026-10-12","dueTime":"09:30","confidence":0.92,"excerpt":"Follow up with cardiology by 2026-10-12 at 09:30","location":"page 1","conflicts":[]}]}
        """;
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", draft)));
  }

  @Test void explicitlySubmittedSourceReturnsReviewOnlyEvidenceAndDepartmentMatches() throws Exception {
    mvc.perform(get("/api/v1/assistant/provider-disclosure").with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.sendsDocumentsToExternalProvider").value(true))
        .andExpect(jsonPath("$.disclosure").value(org.hamcrest.Matchers.containsString("filename and extracted text")));
    var patientResponse = mvc.perform(post("/api/v1/patients").with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "patientIdentifier", "P-DRAFT-427", "firstName", "Alice", "lastName", "Example", "dateOfBirth", "1981-04-03"))))
        .andExpect(status().isCreated()).andReturn();
    long patientId = json.readTree(patientResponse.getResponse().getContentAsString()).path("id").asLong();
    var source = mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", "referral.txt", "text/plain", TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(source.getResponse().getContentAsString()).path("id").asText();
    org.mockito.Mockito.verify(model, org.mockito.Mockito.never()).complete(anyString(), any());
    var response = mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isOk()).andReturn();
    JsonNode draft = json.readTree(response.getResponse().getContentAsString());
    assertThat(draft.path("reviewRequired").asBoolean()).isTrue();
    assertThat(draft.path("saved").asBoolean()).isFalse();
    assertThat(draft.at("/fields/firstName/status").asText()).isEqualTo("SUGGESTED");
    assertThat(draft.at("/fields/address/status").asText()).isEqualTo("MISSING");
    assertThat(draft.at("/fields/firstName/sources/0/excerpt").asText()).isEqualTo("Alice Example");
    assertThat(draft.at("/fields/firstName/sources/0/value").asText()).isEqualTo("Alice");
    assertThat(draft.at("/fields/firstName/sources/0/characterStart").asInt()).isEqualTo(TEXT.indexOf("Alice Example"));
    assertThat(draft.path("matchCandidates").size()).isEqualTo(1);
    assertThat(draft.at("/followUpActions/0/title").asText()).isEqualTo("Follow up with cardiology");
    assertThat(draft.at("/followUpActions/0/dueDate").asText()).isEqualTo("2026-10-12");
    assertThat(draft.at("/followUpActions/0/dueTime").asText()).isEqualTo("09:30");
    assertThat(draft.at("/followUpActions/0/requiresResolution").asBoolean()).isFalse();
    assertThat(draft.at("/followUpActions/0/sources/0/excerpt").asText()).isEqualTo("Follow up with cardiology by 2026-10-12 at 09:30");
    assertThat(draft.at("/followUpActions/0/sources/0/characterStart").asInt()).isEqualTo(TEXT.indexOf("Follow up with cardiology"));
    String draftId = draft.path("draftId").asText();
    mvc.perform(put("/api/v1/assistant/patient-drafts/" + draftId + "/patient").with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("patientId", patientId))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.patientId").value(patientId));
    mvc.perform(get("/api/v1/assistant/patient-drafts/" + draftId).with(user("staff")).header("X-Department-Id", "1"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/assistant/patient-drafts/" + draftId).with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.patientId").value(patientId));
    assertThat(jdbc.queryForObject("select count(*) from patients where patient_identifier='P-DRAFT-427'", Long.class)).isEqualTo(1);
    mvc.perform(delete("/api/v1/assistant/sources/" + sourceId).with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/assistant/patient-drafts/" + draftId).with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isNotFound());
  }

  @Test void doctorNeedsDepartmentScopedPermissionAndUnownedSourceIsHidden() throws Exception {
    var source = mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", "referral.txt", "text/plain", TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(source.getResponse().getContentAsString()).path("id").asText();
    mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("doctor")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/patients").with(user("doctor")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "patientIdentifier", "P-DENIED-428", "firstName", "Denied", "lastName", "Example",
            "dateOfBirth", "1981-04-03"))))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/workspaces/departments/1/members/" + doctorUserId() + "/patient-import")
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("doctor")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isNotFound());
  }

  @Test void contradictoryFollowUpDatesStayUnresolvedAndSourceLinked() throws Exception {
    when(model.complete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", """
            {"followUpActions":[{"title":"Follow up with cardiology","dueDate":"2026-10-12","dueTime":"09:30","confidence":0.9,"excerpt":"Follow up with cardiology by 2026-10-12 at 09:30","conflicts":[{"title":"Follow up with cardiology","dueDate":"2026-10-13","dueTime":"10:00","confidence":0.8,"excerpt":"other form says by 2026-10-13 at 10:00"}]}]}
            """)));
    var source = mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", "referral.txt", "text/plain", TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(source.getResponse().getContentAsString()).path("id").asText();
    var response = mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("admin")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isOk()).andReturn();
    JsonNode draft = json.readTree(response.getResponse().getContentAsString());
    assertThat(draft.at("/followUpActions/0/status").asText()).isEqualTo("CONFLICT");
    assertThat(draft.at("/followUpActions/0/fieldStatuses/dueDate").asText()).isEqualTo("CONFLICT");
    assertThat(draft.at("/followUpActions/0/requiresResolution").asBoolean()).isTrue();
    assertThat(draft.at("/followUpActions/0/conflicts/0/source/excerpt").asText()).isEqualTo("other form says by 2026-10-13 at 10:00");
  }

  @Test void proposedValuesMustBeSupportedByTheirOwnExcerpt() throws Exception {
    var source = mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", "referral.txt", "text/plain", TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(source.getResponse().getContentAsString()).path("id").asText();

    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", """
        {"patientIdentifier":[{"value":"P-SPOOFED","confidence":0.99,"excerpt":"Alice Example"}],"followUpActions":[]}
        """)));
    mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isServiceUnavailable());

    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", """
        {"followUpActions":[{"title":"Follow up with cardiology","dueDate":"2030-01-01","dueTime":null,"confidence":0.99,"excerpt":"Follow up with cardiology"}]}
        """)));
    mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isServiceUnavailable());
  }

  private long doctorUserId() {
    return jdbc.queryForObject("select id from app_users where username='doctor'", Long.class);
  }
}
