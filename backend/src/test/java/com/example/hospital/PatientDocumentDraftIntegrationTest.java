package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
  private static final String TEXT = "Patient ID: P-DRAFT-427\nName: Alice Example\nDate of birth: 1981-04-03\nPhone: +359888123456";

  @BeforeEach void setup() throws Exception {
    when(model.identifier()).thenReturn("patient-draft-fixture");
    String draft = """
        {"patientIdentifier":[{"value":"P-DRAFT-427","confidence":0.98,"excerpt":"P-DRAFT-427","location":"page 1"}],
         "firstName":[{"value":"Alice","confidence":0.94,"excerpt":"Alice Example","location":"page 1"}],
         "lastName":[{"value":"Example","confidence":0.94,"excerpt":"Alice Example","location":"page 1"}],
         "dateOfBirth":[{"value":"1981-04-03","confidence":0.9,"excerpt":"1981-04-03","location":"page 1"}],
         "address":[],"phoneNumber":[{"value":"+359888123456","confidence":0.91,"excerpt":"+359888123456","location":"page 1"}]}
        """;
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("submitPatientDraft", Map.of("draft_json", draft)));
  }

  @Test void explicitlySubmittedSourceReturnsReviewOnlyEvidenceAndDepartmentMatches() throws Exception {
    mvc.perform(post("/api/v1/patients").with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "patientIdentifier", "P-DRAFT-427", "firstName", "Alice", "lastName", "Example", "dateOfBirth", "1981-04-03"))))
        .andExpect(status().isCreated());
    mvc.perform(put("/api/v1/workspaces/departments/1/members/" + doctorUserId() + "/patient-import")
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    var source = mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", "referral.txt", "text/plain", TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user("doctor")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn();
    String sourceId = json.readTree(source.getResponse().getContentAsString()).path("id").asText();
    var response = mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("doctor")).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isOk()).andReturn();
    JsonNode draft = json.readTree(response.getResponse().getContentAsString());
    assertThat(draft.path("reviewRequired").asBoolean()).isTrue();
    assertThat(draft.path("saved").asBoolean()).isFalse();
    assertThat(draft.at("/fields/firstName/status").asText()).isEqualTo("SUGGESTED");
    assertThat(draft.at("/fields/address/status").asText()).isEqualTo("MISSING");
    assertThat(draft.at("/fields/firstName/sources/0/excerpt").asText()).isEqualTo("Alice Example");
    assertThat(draft.at("/fields/firstName/sources/0/characterStart").asInt()).isEqualTo(TEXT.indexOf("Alice Example"));
    assertThat(draft.path("matchCandidates").size()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from patients where patient_identifier='P-DRAFT-427'", Long.class)).isEqualTo(1);
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
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/workspaces/departments/1/members/" + doctorUserId() + "/patient-import")
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/assistant/patient-drafts").with(user("doctor")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceId", sourceId))))
        .andExpect(status().isNotFound());
  }

  private long doctorUserId() {
    return jdbc.queryForObject("select id from app_users where username='doctor'", Long.class);
  }
}
