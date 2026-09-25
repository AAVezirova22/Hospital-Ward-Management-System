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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
  static void database(DynamicPropertyRegistry r) { HospitalSupport.database(r); }
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean AiModelClient model;

  @BeforeEach void setup() { when(model.identifier()).thenReturn("workflow-fixture"); }
  String unique() { return UUID.randomUUID().toString().substring(0, 8); }
  JsonNode body(ResultActions result) throws Exception {
    var response = result.andReturn().getResponse();
    if (response.getStatus() != 200)
      throw new AssertionError("Expected HTTP 200 but received " + response.getStatus() + ": " + response.getContentAsString());
    return json.readTree(response.getContentAsString());
  }
  ResultActions postJson(String user, String path, Object data) throws Exception {
    return mvc.perform(post("/api/v1" + path).with(user(user)).with(csrf())
        .header("X-Department-Id", "1").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)));
  }
  ResultActions putJson(String user, String path, Object data) throws Exception {
    return mvc.perform(put("/api/v1" + path).with(user(user)).with(csrf())
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
        step("r", "createRoom", Map.of("roomNumber", "A1", "bedCount", 2, "active", true,
            "capabilities", List.of("oxygen"))),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", "$d", "roomId", "$r",
            "requiredRoomCapabilities", List.of("oxygen")))));
    when(model.complete(anyString(), any())).thenAnswer(inv -> {
      AiModelClient.Context context = inv.getArgument(1);
      assertThat(context.sources().getFirst().get("text")).contains(marker);
      return new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", plan));
    });
    var proposal = body(postJson("admin", "/assistant/messages",
        Map.of("message", "Set up the hospital from the file", "sourceIds", List.of(source.get("id").asText()))));
    assertThat(proposal.get("responseType").asText()).isEqualTo("WORKFLOW_PROPOSAL");
    assertThat(proposal.at("/data/workflow/steps/3/fields/capabilities/0").asText())
        .as("Workflow proposal: %s", proposal.toPrettyString()).isEqualTo("oxygen");
    assertThat(proposal.at("/data/workflow/steps/4/fields/requiredRoomCapabilities/0").asText()).isEqualTo("oxygen");
    long action = proposal.at("/data/action/id").asLong();
    var before = body(mvc.perform(get("/api/v1/workspaces").with(user("admin"))));
    assertThat(before.toString()).doesNotContain("Import " + marker);
    var result = body(postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()));
    long department = result.get("departmentId").asLong();
    var patients = body(mvc.perform(get("/api/v1/patients").with(user("admin")).header("X-Department-Id", department)));
    assertThat(patients.toString()).contains("P-" + marker);
var admissions =
    json.readTree(
        mvc.perform(
                get("/api/v1/admissions")
                    .with(user("admin"))
                    .header("X-Department-Id", department))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());

assertThat(admissions.get("items").size()).isEqualTo(1);
assertThat(admissions.get("totalElements").asLong()).isEqualTo(1);
assertThat(admissions.get("items").get(0).toString())
    .contains("requiredRoomCapabilities")
    .contains("oxygen");
    var home = body(mvc.perform(get("/api/v1/patients").with(user("admin")).header("X-Department-Id", "1")));
    assertThat(home.toString()).doesNotContain("P-" + marker);
    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()).andExpect(status().isConflict());
  }

  @Test void workflowFieldEvidenceIsVerifiedAndUnresolvedFieldsNeedExplicitReview() throws Exception {
    String marker = unique();
    String excerpt = "Patient identifier: SOURCE-" + marker;
    JsonNode source = upload("admin", "patients.txt", excerpt);
    String patientIdentifier = "P-" + marker;
    String inputPlan = json.writeValueAsString(Map.of(
        "title", "Prepare patient import",
        "steps", List.of(Map.of(
            "key", "patient",
            "operation", "createPatient",
            "source", "patients.txt",
            "fields", Map.of("patientIdentifier", patientIdentifier, "firstName", "Original",
                "lastName", "Patient", "dateOfBirth", "1990-01-01"),
            "evidence", Map.of(
                "patientIdentifier", Map.of("status", "UNCERTAIN", "confidence", 0.42,
                    "sources", List.of(Map.of("sourceId", source.get("id").asText(), "location", "line 1", "excerpt", excerpt)),
                    "conflicts", List.of()),
                "firstName", Map.of("status", "SUPPORTED", "confidence", 0.99,
                    "sources", List.of(Map.of("sourceId", "not-owned", "sourceName", "private.txt",
                        "location", "row 1", "excerpt", "Original")),
                    "conflicts", List.of()))))));

    JsonNode proposal = propose(inputPlan);
    assertThat(proposal.at("/data/workflow/steps/0/evidence/patientIdentifier/sources/0/verified").asBoolean()).isTrue();
    assertThat(proposal.at("/data/workflow/steps/0/evidence/patientIdentifier/sources/0/sourceName").asText()).isEqualTo("patients.txt");
    assertThat(proposal.at("/data/workflow/steps/0/evidence/patientIdentifier/sources/0/characterStart").asInt()).isZero();
    assertThat(proposal.at("/data/workflow/steps/0/evidence/patientIdentifier/requiresDecision").asBoolean()).isTrue();
    assertThat(proposal.at("/data/workflow/steps/0/evidence/firstName/sources/0/verified").asBoolean()).isFalse();
    assertThat(proposal.at("/data/workflow/steps/0/evidence/firstName/requiresDecision").asBoolean()).isTrue();

    long action = proposal.at("/data/action/id").asLong();
    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of())
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFLOW_REVIEW_REQUIRED"));
    assertThat(jdbc.queryForObject("select count(*) from patients where patient_identifier=?", Integer.class, patientIdentifier)).isZero();

    var decisions = Map.of("fieldDecisions", List.of(
        Map.of("stepKey", "patient", "field", "patientIdentifier", "decision", "ACCEPTED"),
        Map.of("stepKey", "patient", "field", "firstName", "decision", "EDITED", "value", "Clinician")));
    postJson("admin", "/ai-actions/" + action + "/confirm", decisions).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("select first_name from patients where patient_identifier=?", String.class, patientIdentifier))
        .isEqualTo("Clinician");
  }

  @Test void assistantRoomSearchExplainsCapabilityAndAvailabilityExclusions() throws Exception {
    when(model.identifier()).thenReturn("local-command-model");
    String compatibleNumber = "R-" + unique();
    String missingNumber = "R-" + unique();
    String inactiveNumber = "R-" + unique();
    JsonNode compatible = createRoom(compatibleNumber, List.of("oxygen", "isolation"), true);
    createRoom(missingNumber, List.of("oxygen"), true);
    createRoom(inactiveNumber, List.of("oxygen", "isolation"), false);
    when(model.complete(anyString(), any()))
        .thenReturn(new AiModelClient.ToolCall("getAvailableRooms", Map.of(
            "minimumFreeBeds", "1", "requiredCapabilities", " Oxygen, isolation ")));

    JsonNode result = body(postJson("admin", "/assistant/messages", Map.of("message", "Find a room")));

    assertThat(result.path("responseType").asText()).isEqualTo("ROOM_LIST");
    assertThat(result.at("/data/rooms").size()).isEqualTo(1);
    assertThat(result.at("/data/rooms/0/roomNumber").asText()).isEqualTo(compatibleNumber);
    assertThat(result.at("/data/excludedRooms").toString()).contains(missingNumber, "isolation", inactiveNumber, "inactive");
  }

  @Test void assistantAdmissionRejectsIncompatibleRoomsAndPersistsReviewedRequirements() throws Exception {
    String marker = unique();
    JsonNode patient = createPatient(marker);
    JsonNode incompatible = createRoom("R-" + unique(), List.of("oxygen"), true);
    JsonNode compatible = createRoom("R-" + unique(), List.of("oxygen", "isolation"), true);
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareAdmission", Map.of(
        "patientQuery", patient.get("patientIdentifier").asText(),
        "doctorQuery", "Dimitrova",
        "roomNumber", incompatible.get("roomNumber").asText(),
        "requiredRoomCapabilities", "oxygen, isolation")));
    postJson("admin", "/assistant/messages", Map.of("message", "Prepare admission"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("isolation")));

    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareAdmission", Map.of(
        "patientQuery", patient.get("patientIdentifier").asText(),
        "doctorQuery", "Dimitrova",
        "roomNumber", compatible.get("roomNumber").asText(),
        "requiredRoomCapabilities", "oxygen, isolation")));
    JsonNode proposal = body(postJson("admin", "/assistant/messages", Map.of("message", "Prepare admission")));
    assertThat(proposal.path("responseType").asText()).isEqualTo("CONFIRMATION_CARD");
    assertThat(proposal.at("/data/requiredRoomCapabilities").toString()).contains("oxygen", "isolation");
    assertThat(proposal.at("/data/destination/capabilities").toString()).contains("oxygen", "isolation");
    long action = proposal.at("/data/action/id").asLong();
    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()).andExpect(status().isOk());
    assertThat(jdbc.queryForList(
        "select capability from admission_room_requirements r join admissions a on a.id=r.admission_id where a.patient_id=? order by capability",
        String.class, patient.get("id").asLong())).containsExactly("isolation", "oxygen");
  }

  @Test void assistantTransferUsesTheAdmissionRequirementsForRoomSelection() throws Exception {
    String marker = unique();
    JsonNode patient = createPatient(marker);
    JsonNode currentRoom = createRoom("S-" + marker, List.of("oxygen"), true);
    JsonNode incompatible = createRoom("I-" + marker, List.of("isolation"), true);
    JsonNode compatible = createRoom("D-" + marker, List.of("oxygen", "isolation"), true);
    JsonNode admission = json.readTree(postJson("admin", "/admissions", Map.of(
        "patientId", patient.get("id").asLong(), "doctorId", 1,
        "roomId", currentRoom.get("id").asLong(),
        "requiredRoomCapabilities", List.of("oxygen")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareTransfer", Map.of(
        "patientQuery", patient.get("patientIdentifier").asText(),
        "roomNumber", incompatible.get("roomNumber").asText())));
    postJson("admin", "/assistant/messages", Map.of("message", "Prepare transfer"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("oxygen")));

    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareTransfer", Map.of(
        "patientQuery", patient.get("patientIdentifier").asText(),
        "roomNumber", compatible.get("roomNumber").asText())));
    JsonNode proposal = body(postJson("admin", "/assistant/messages", Map.of("message", "Prepare transfer")));
    assertThat(proposal.path("responseType").asText()).isEqualTo("CONFIRMATION_CARD");
    assertThat(proposal.at("/data/requiredRoomCapabilities").toString()).contains("oxygen");
    long action = proposal.at("/data/action/id").asLong();
    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of()).andExpect(status().isOk());

    assertThat(jdbc.queryForList(
        "select capability from admission_room_requirements where admission_id = ? order by capability",
        String.class, admission.get("id").asLong())).containsExactly("oxygen");
    assertThat(jdbc.queryForObject(
        "select room_id from room_assignments where admission_id = ? and released_at is null",
        Long.class, admission.get("id").asLong())).isEqualTo(compatible.get("id").asLong());
  }

  @Test void workflowProposalRejectsIncompatibleAdmissionAndTransferDestinations() throws Exception {
    String marker = unique();
    String invalidAdmission = plan(List.of(
        step("p", "createPatient", Map.of("patientIdentifier", "P-" + marker, "firstName", "Test", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("d", "createDoctor", Map.of("doctorIdentifier", "D-" + marker, "firstName", "Test", "lastName", "Doctor", "specialty", "Medicine", "active", true)),
        step("r", "createRoom", Map.of("roomNumber", "A-" + marker, "bedCount", 2, "active", true, "capabilities", List.of("oxygen"))),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", "$d", "roomId", "$r", "requiredRoomCapabilities", List.of("isolation")))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", invalidAdmission)));
    postJson("admin", "/assistant/messages", Map.of("message", "Propose workflow"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("isolation")));

    String invalidTransfer = plan(List.of(
        step("p", "createPatient", Map.of("patientIdentifier", "P2-" + marker, "firstName", "Test", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("d", "createDoctor", Map.of("doctorIdentifier", "D2-" + marker, "firstName", "Test", "lastName", "Doctor", "specialty", "Medicine", "active", true)),
        step("r1", "createRoom", Map.of("roomNumber", "B-" + marker, "bedCount", 2, "active", true, "capabilities", List.of("oxygen"))),
        step("r2", "createRoom", Map.of("roomNumber", "C-" + marker, "bedCount", 2, "active", true, "capabilities", List.of("isolation"))),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", "$d", "roomId", "$r1", "requiredRoomCapabilities", List.of("oxygen"))),
        step("t", "transfer", Map.of("admissionId", "$a", "roomId", "$r2", "reason", "Test", "version", 0))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall("prepareWorkflow", Map.of("plan", invalidTransfer)));
    postJson("admin", "/assistant/messages", Map.of("message", "Propose workflow"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("oxygen")));
  }

  @Test void workflowProposalRejectsUnavailableRoomsAndPlannedOvercapacity() throws Exception {
    String marker = unique();
    JsonNode fullRoom = createRoom("F-" + marker, List.of(), true);
    JsonNode existingPatient = createPatient(marker);
    postJson("admin", "/admissions", Map.of(
        "patientId", existingPatient.get("id").asLong(), "doctorId", 1,
        "roomId", fullRoom.get("id").asLong())).andExpect(status().isCreated());

    String fullRoomPlan = plan(List.of(
        step("p", "createPatient", Map.of("patientIdentifier", "P-full-" + marker,
            "firstName", "Test", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", 1,
            "roomId", fullRoom.get("id").asLong()))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall(
        "prepareWorkflow", Map.of("plan", fullRoomPlan)));
    postJson("admin", "/assistant/messages", Map.of("message", "Propose workflow"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));

    JsonNode inactiveRoom = createRoom("X-" + marker, List.of(), false);
    String inactiveRoomPlan = plan(List.of(
        step("p", "createPatient", Map.of("patientIdentifier", "P-inactive-" + marker,
            "firstName", "Test", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", 1,
            "roomId", inactiveRoom.get("id").asLong()))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall(
        "prepareWorkflow", Map.of("plan", inactiveRoomPlan)));
    postJson("admin", "/assistant/messages", Map.of("message", "Propose workflow"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));

    String overCapacityPlan = plan(List.of(
        step("p1", "createPatient", Map.of("patientIdentifier", "P1-plan-" + marker,
            "firstName", "First", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("p2", "createPatient", Map.of("patientIdentifier", "P2-plan-" + marker,
            "firstName", "Second", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("r", "createRoom", Map.of("roomNumber", "P-" + marker,
            "bedCount", 1, "active", true)),
        step("a1", "admit", Map.of("patientId", "$p1", "doctorId", 1, "roomId", "$r")),
        step("a2", "admit", Map.of("patientId", "$p2", "doctorId", 1, "roomId", "$r"))));
    when(model.complete(anyString(), any())).thenReturn(new AiModelClient.ToolCall(
        "prepareWorkflow", Map.of("plan", overCapacityPlan)));
    postJson("admin", "/assistant/messages", Map.of("message", "Propose workflow"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));
  }

  @Test void workflowConfirmationRechecksRoomCapabilitiesBeforeAnyWrite() throws Exception {
    String marker = unique();
    JsonNode room = createRoom("V-" + marker, List.of("oxygen"), true);
    String plan = plan(List.of(
        step("p", "createPatient", Map.of("patientIdentifier", "P-" + marker, "firstName", "Test", "lastName", "Patient", "dateOfBirth", "1990-01-01")),
        step("a", "admit", Map.of("patientId", "$p", "doctorId", 1, "roomId", room.get("id").asLong(), "requiredRoomCapabilities", List.of("oxygen")))));
    JsonNode proposal = propose(plan);
    long action = proposal.at("/data/action/id").asLong();
    putJson("admin", "/rooms/" + room.get("id").asLong(), Map.of(
        "roomNumber", room.get("roomNumber").asText(), "bedCount", 1, "active", true,
        "capabilities", List.of("isolation"), "version", room.get("version").asLong()))
        .andExpect(status().isOk());

    postJson("admin", "/ai-actions/" + action + "/confirm", Map.of())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"));
    JsonNode patients = body(mvc.perform(get("/api/v1/patients").with(user("admin")).header("X-Department-Id", "1")));
    assertThat(patients.toString()).doesNotContain("P-" + marker);
  }

  private JsonNode createRoom(String number, List<String> capabilities, boolean active) throws Exception {
    return json.readTree(postJson("admin", "/rooms", Map.of(
        "roomNumber", number, "bedCount", 1, "active", active, "capabilities", capabilities))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  }

  private JsonNode createPatient(String marker) throws Exception {
    return json.readTree(postJson("admin", "/patients", Map.of(
        "patientIdentifier", "P-" + marker, "firstName", "Patient", "lastName", "Capability", "dateOfBirth", "1990-01-01"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
