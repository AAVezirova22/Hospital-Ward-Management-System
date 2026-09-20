package com.example.hospital;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.hospital.ai.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = {"app.seed=true", "app.bootstrap-password=IntegrationPassword123!", "app.ai.rate-limit=10000"})
@AutoConfigureMockMvc
class AiWorkflowIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) { HospitalIntegrationTest.database(r); }
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean AiModelClient model;

  @BeforeEach void setup() { when(model.identifier()).thenReturn("workflow-fixture"); }
  String unique() { return UUID.randomUUID().toString().substring(0, 8); }
  JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  ResultActions postJson(String user, String path, Object data) throws Exception {
    return mvc.perform(post("/api/v1" + path).with(user(user)).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)));
  }
  JsonNode upload(String user, String name, String content) throws Exception {
    return body(mvc.perform(multipart("/api/v1/assistant/sources")
        .file(new MockMultipartFile("file", name, "text/plain", content.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .with(user(user)).with(csrf()).header("X-Department-Id", "1")));
  }
  Map<String, Object> step(String key, String operation, Map<String, Object> fields) {
    return Map.of("key", key, "operation", operation, "source", "setup.csv", "fields", fields);
  }
  String plan(List<Map<String, Object>> steps) throws Exception {
    return json.writeValueAsString(Map.of("title", "Build the requested workspace", "steps", steps));
  }
  JsonNode propose(String plan) throws Exception {
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", plan)));
    return body(postJson("admin", "/assistant/messages", Map.of("message", "Build this workflow")));
  }

  @Test void uploadedDataBuildsLinkedHospitalWorkflowOnlyAfterConfirmation() throws Exception {
    String marker = unique();
    var source = upload("admin", "setup.csv", "name,department\nImport " + marker + ",Operations");
    String plan = plan(List.of(
        step("h", "createHospital", Map.of("name", "Import " + marker, "departmentName", "Operations")),
        step("p", "createPatient", Map.of("patientIdentifier", "P-" + marker, "firstName", "Imported", "lastName", "Person", "dateOfBirth", "1990-01-01")),
        step("d", "createDoctor", Map.of("doctorIdentifier", "D-" + marker, "firstName", "Imported", "lastName", "Doctor", "specialty", "Medicine", "active", true)),
        step("r", "createRoom", Map.of("roomNumber", "A1", "bedCount", 2, "active", true)),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", "$d", "roomId", "$r"))));
    when(model.complete(anyString(), any())).thenAnswer(inv -> {
      AiModelClient.Context context = inv.getArgument(1);
      assertThat(context.sources().getFirst().get("text")).contains(marker);
      return new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", plan));
    });
    var proposal = body(postJson("admin", "/assistant/messages",
        Map.of("message", "Set up the hospital from the file", "sourceIds", List.of(source.get("id").asText()))));
    assertThat(proposal.get("responseType").asText()).isEqualTo("WORKFLOW_PROPOSAL");
    long action = proposal.at("/data/action/id").asLong();
    var before = body(mvc.perform(get("/api/v1/workspaces").with(user("admin"))));
    assertThat(before.toString()).doesNotContain("Import " + marker);
    var result = body(postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()));
    long department = result.get("departmentId").asLong();
    var patients = body(mvc.perform(get("/api/v1/patients").with(user("admin")).header("X-Department-Id", department)));
    assertThat(patients.toString()).contains("P-" + marker);
    var admissions = body(mvc.perform(get("/api/v1/admissions").with(user("admin")).header("X-Department-Id", department)));
    assertThat(admissions.size()).isEqualTo(1);
    var home = body(mvc.perform(get("/api/v1/patients").with(user("admin")).header("X-Department-Id", "1")));
    assertThat(home.toString()).doesNotContain("P-" + marker);
    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()).andExpect(status().isConflict());
  }

  @Test void failedWorkflowRollsBackHospitalAndAllPriorSteps() throws Exception {
    String name = "Rollback " + unique();
    var proposal = propose(plan(List.of(
        step("h", "createHospital", Map.of("name", name, "departmentName", "Ward")),
        step("r1", "createRoom", Map.of("roomNumber", "duplicate", "bedCount", 1, "active", true)),
        step("r2", "createRoom", Map.of("roomNumber", "duplicate", "bedCount", 1, "active", true)))));
    long id = proposal.at("/data/action/id").asLong();
    postJson("admin", "/ai-actions/" + id + "/confirm", Map.of()).andExpect(status().isConflict());
    var hospitals = body(mvc.perform(get("/api/v1/workspaces").with(user("admin"))));
    assertThat(hospitals.toString()).doesNotContain(name);
    assertThat(body(mvc.perform(get("/api/v1/ai-actions/" + id).with(user("admin")).header("X-Department-Id", "1")))
        .path("status").asText()).isEqualTo("PENDING");
  }

  @Test void referencesRolesOwnershipAndCancellationAreEnforced() throws Exception {
    var invalid = plan(List.of(step("a", "admit", Map.of("patientId", "$missing", "doctorId", 1, "roomId", 1))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", invalid)));
    postJson("admin", "/assistant/messages", Map.of("message", "Import")).andExpect(status().isBadRequest());
    String valid = plan(List.of(step("r", "createRoom", Map.of("roomNumber", unique(), "bedCount", 1, "active", true))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", valid)));
    postJson("doctor", "/assistant/messages", Map.of("message", "Import")).andExpect(status().isForbidden());
    var proposal = propose(valid);
    long id = proposal.at("/data/action/id").asLong();
    postJson("staff", "/ai-actions/" + id + "/confirm", Map.of()).andExpect(status().isForbidden());
    postJson("admin", "/ai-actions/" + id + "/cancel", Map.of()).andExpect(status().isOk());
    postJson("admin", "/ai-actions/" + id + "/confirm", Map.of()).andExpect(status().isConflict());
  }

  @Test void sourcesAreOwnedScopedRemovableAndReadRequestsCannotInventIds() throws Exception {
    var source = upload("admin", "notes.txt", "A private source");
    var input = Map.of("message", "Summarize", "sourceIds", List.of(source.get("id").asText()));
    postJson("staff", "/assistant/messages", input).andExpect(status().isNotFound());
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("readConnectedFiles", Map.of("ids", "outside")));
    postJson("admin", "/assistant/messages", Map.of("message", "Find files", "connectedFiles", List.of(Map.of("id", "inside", "name", "folder/notes.txt"))))
        .andExpect(status().isBadRequest());
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("readConnectedFiles", Map.of("ids", "inside")));
    postJson("admin", "/assistant/messages", Map.of("message", "Find files", "connectedFiles", List.of(Map.of("id", "inside", "name", "folder/notes.txt"))))
        .andExpect(jsonPath("$.responseType").value("FILE_REQUEST")).andExpect(jsonPath("$.data.ids[0]").value("inside"));
    mvc.perform(delete("/api/v1/assistant/sources/" + source.get("id").asText()).with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isOk());
    postJson("admin", "/assistant/messages", input).andExpect(status().isNotFound());
  }

  @Test void queryResultsAreFedBackBeforePlanning() throws Exception {
    when(model.complete(anyString(), any())).thenAnswer(inv -> {
      AiModelClient.Context ctx = inv.getArgument(1);
      if (ctx.observations().isEmpty()) return new AiModelClient.ToolCall("getWorkflowCatalogue", Map.of());
      assertThat(ctx.observations().getFirst().get("tool")).isEqualTo("getWorkflowCatalogue");
      return new AiModelClient.ToolCall("respond", Map.of("message", "Which of these rooms should I use?"));
    });
    postJson("admin", "/assistant/messages", Map.of("message", "Help place these records"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Which of these rooms should I use?"));
    verify(model, times(2)).complete(anyString(), any());
  }

  @Test void uploadsRejectUnsupportedAndOverlongDocuments() throws Exception {
    mvc.perform(multipart("/api/v1/assistant/sources").file(new MockMultipartFile("file", "run.exe", "text/plain", "data".getBytes()))
        .with(user("admin")).with(csrf())).andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/v1/assistant/sources").file(new MockMultipartFile("file", "large.txt", "text/plain", "x".repeat(50000).getBytes()))
        .with(user("admin")).with(csrf())).andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/v1/assistant/sources").file(new MockMultipartFile("file", "note.txt", "text/plain", "data".getBytes()))
        .with(user("admin"))).andExpect(status().isForbidden());
  }

  @Test void expiredWorkflowCannotBeConfirmed() throws Exception {
    var proposal = propose(plan(List.of(step("r", "createRoom",
        Map.of("roomNumber", unique(), "bedCount", 1, "active", true)))));
    long id = proposal.at("/data/action/id").asLong();
    jdbc.update("update ai_pending_actions set expires_at=now()-interval '1 minute' where id=?", id);
    postJson("admin", "/ai-actions/" + id + "/confirm", Map.of())
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTION_EXPIRED"));
  }

  @Test void extractsWordExcelAndPdfWithoutCustomParsers() throws Exception {
    List<byte[]> bytes = new ArrayList<>();
    try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
         var out = new java.io.ByteArrayOutputStream()) {
      doc.createParagraph().createRun().setText("Word workflow source");
      doc.write(out); bytes.add(out.toByteArray());
    }
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
         var out = new java.io.ByteArrayOutputStream()) {
      book.createSheet("Workflow").createRow(0).createCell(0).setCellValue("Excel workflow source");
      book.write(out); bytes.add(out.toByteArray());
    }
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
         var out = new java.io.ByteArrayOutputStream()) {
      var page = new org.apache.pdfbox.pdmodel.PDPage(); pdf.addPage(page);
      try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
        stream.beginText();
        stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700); stream.showText("PDF workflow source"); stream.endText();
      }
      pdf.save(out); bytes.add(out.toByteArray());
    }
    var extensions = List.of("docx", "xlsx", "pdf");
    for (int i = 0; i < extensions.size(); i++) {
      var source = body(mvc.perform(multipart("/api/v1/assistant/sources")
          .file(new MockMultipartFile("file", "source." + extensions.get(i), "application/octet-stream", bytes.get(i)))
          .with(user("admin")).with(csrf()).header("X-Department-Id", "1")));
      doAnswer(inv -> {
        AiModelClient.Context ctx = inv.getArgument(1);
        assertThat(ctx.sources().getFirst().get("text")).contains("workflow source");
        return new AiModelClient.ToolCall("respond", Map.of("message", "Read successfully."));
      }).when(model).complete(anyString(), any());
      postJson("admin", "/assistant/messages", Map.of("message", "Read", "sourceIds", List.of(source.get("id").asText())))
          .andExpect(status().isOk());
    }
  }
}
