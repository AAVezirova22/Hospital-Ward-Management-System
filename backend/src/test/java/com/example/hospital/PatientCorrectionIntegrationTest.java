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

/** Patient-submitted demographic corrections (#159): submit, staff review, audit trail. */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class PatientCorrectionIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void approvedCorrectionUpdatesTheRecordAndLeavesBothActsInTheAuditTrail() throws Exception {
    long patientId = createPatient("Sofia", "Petrova", "10 Old Street", null);
    String username = createPatientAccount(patientId);

    // A patient proposes a change; the record itself is untouched until staff approve.
    JsonNode request = patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address",
        "requestedValue", "42 New Avenue",
        "reason", "We moved last month."), username);
    assertThat(request.path("status").asText()).isEqualTo("PENDING");
    assertThat(request.path("previousValue").asText()).isEqualTo("10 Old Street");
    assertThat(request.path("requestedValue").asText()).isEqualTo("42 New Avenue");
    assertThat(request.path("version").asLong()).isZero();
    assertThat(patientField(patientId, "address")).isEqualTo("10 Old Street");

    long requestId = request.path("id").asLong();
    assertThat(jdbc.queryForObject(
        "select count(*) from patient_correction_requests where id=? and status='PENDING'",
        Integer.class, requestId)).isEqualTo(1);

    // The staff queue lists it, joined to the patient it concerns.
    JsonNode queue = staffGet("/api/v1/patient-correction-requests?status=PENDING", "admin");
    assertThat(queue.path("total").asInt()).isPositive();
    boolean listed = false;
    for (JsonNode row : queue.path("requests")) {
      if (row.path("id").asLong() == requestId) {
        listed = true;
        assertThat(row.path("patientId").asLong()).isEqualTo(patientId);
        assertThat(row.path("patientFirstName").asText()).isEqualTo("Sofia");
        assertThat(row.path("fieldName").asText()).isEqualTo("ADDRESS");
      }
    }
    assertThat(listed).as("request appears in the staff queue").isTrue();

    // A stale decision is refused rather than applied blind.
    MvcResult stale = mvc.perform(adminPost("/api/v1/patient-correction-requests/"
            + requestId + "/approve", Map.of("version", 99L)))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(stale.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("STALE_STATE");
    assertThat(patientField(patientId, "address")).isEqualTo("10 Old Street");

    // Approval applies the value and records who decided.
    JsonNode approved = json.readTree(mvc.perform(adminPost("/api/v1/patient-correction-requests/"
            + requestId + "/approve", Map.of("version", 0L, "note", "Address confirmed by phone.")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.path("reviewNote").asText()).isEqualTo("Address confirmed by phone.");
    assertThat(patientField(patientId, "address")).isEqualTo("42 New Avenue");

    // Proposal, decision and the record change are all separately auditable.
    assertThat(auditCount(requestId, "PATIENT_CORRECTION_REQUESTED")).isEqualTo(1);
    assertThat(auditCount(requestId, "PATIENT_CORRECTION_APPROVED")).isEqualTo(1);
    assertThat(auditCount(patientId, "PATIENT_UPDATED")).isEqualTo(1);

    // A second decision on the same request is refused.
    mvc.perform(adminPost("/api/v1/patient-correction-requests/" + requestId + "/reject",
            Map.of("version", 0L)))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectedCorrectionLeavesTheRecordAndAllowsANewRequest() throws Exception {
    long patientId = createPatient("Georgi", "Dimitrov", "5 Old Road", null);
    String username = createPatientAccount(patientId);

    long firstId = patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "7 Wrong Road"), username)
        .path("id").asLong();
    JsonNode rejected = json.readTree(mvc.perform(adminPost(
            "/api/v1/patient-correction-requests/" + firstId + "/reject",
            Map.of("version", 0L, "note", "Cannot verify this address.")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
    assertThat(patientField(patientId, "address")).isEqualTo("5 Old Road");
    assertThat(auditCount(firstId, "PATIENT_CORRECTION_REJECTED")).isEqualTo(1);
    assertThat(auditCount(patientId, "PATIENT_UPDATED")).isZero();

    // Once decided, the patient may ask again.
    JsonNode second = patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "8 Right Road"), username);
    assertThat(second.path("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void onlyOneOpenRequestPerFieldAndUnchangedValuesAreRefused() throws Exception {
    long patientId = createPatient("Marta", "Georgieva", "9 Same Road", null);
    String username = createPatientAccount(patientId);

    // A value identical to the current one is not a correction.
    MvcResult same = patientPostExpect(409, "/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "9 Same Road"), username);
    assertThat(json.readTree(same.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("ALREADY_CORRECT");

    patientPost("/api/v1/portal/correction-requests",
        Map.of("fieldName", "address", "requestedValue", "11 Different Road"), username);
    MvcResult duplicate = patientPostExpect(409, "/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "12 Also Different"), username);
    assertThat(json.readTree(duplicate.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("REQUEST_PENDING");

    // A different field is still allowed while the first is open.
    assertThat(patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "phoneNumber", "requestedValue", "+359888123456"), username)
        .path("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void invalidValuesAndUnsupportedFieldsAreRefused() throws Exception {
    long patientId = createPatient("Niki", "Todorov", "1 Any Road", null);
    String username = createPatientAccount(patientId);

    // Phone numbers follow the same E.164 rule as the patient form.
    MvcResult phone = patientPostExpect(400, "/api/v1/portal/correction-requests", Map.of(
        "fieldName", "phoneNumber", "requestedValue", "0888123456"), username);
    assertThat(json.readTree(phone.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("INVALID_PHONE");

    // Clinical fields are not patient-correctable.
    MvcResult dob = patientPostExpect(400, "/api/v1/portal/correction-requests", Map.of(
        "fieldName", "dateOfBirth", "requestedValue", "1990-01-01"), username);
    assertThat(json.readTree(dob.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("UNSUPPORTED_FIELD");

    assertThat(jdbc.queryForObject("select count(*) from patient_correction_requests where patient_id=?",
        Integer.class, patientId)).isZero();
  }

  @Test
  void approvalRefusesWhenTheRecordChangedAfterTheRequestWasRaised() throws Exception {
    long patientId = createPatient("Elena", "Todorova", "3 Original Road", null);
    String username = createPatientAccount(patientId);
    long id = patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "4 Requested Road"), username)
        .path("id").asLong();

    // Someone edits the record out of band before the request is decided.
    jdbc.update("update patients set address=? where id=?", "5 Clinically Updated Road", patientId);

    MvcResult conflict = mvc.perform(adminPost("/api/v1/patient-correction-requests/"
            + id + "/approve", Map.of("version", 0L)))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(conflict.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("PATIENT_CHANGED");
    assertThat(patientField(patientId, "address")).isEqualTo("5 Clinically Updated Road");
    assertThat(jdbc.queryForObject(
        "select status from patient_correction_requests where id=?", String.class, id))
        .isEqualTo("PENDING");
  }

  @Test
  void aPatientSeesOnlyTheirOwnRequestsAndCannotUseTheStaffEndpoints() throws Exception {
    long mineId = createPatient("Own", "Owner", "1 Mine Road", null);
    long theirsId = createPatient("Other", "Patient", "2 Theirs Road", null);
    String myUsername = createPatientAccount(mineId);
    createPatientAccount(theirsId);
    long myRequest = patientPost("/api/v1/portal/correction-requests", Map.of(
        "fieldName", "address", "requestedValue", "3 Mine New Road"), myUsername)
        .path("id").asLong();

    JsonNode mine = json.readTree(mvc.perform(get("/api/v1/portal/correction-requests")
            .with(user(myUsername).roles("PATIENT")).with(csrf()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(mine.size()).isEqualTo(1);
    assertThat(mine.get(0).path("id").asLong()).isEqualTo(myRequest);
    assertThat(jdbc.queryForObject(
        "select count(*) from patient_correction_requests where patient_id=?", Integer.class, theirsId))
        .isZero();

    // A patient cannot reach the staff queue or the decision endpoints.
    mvc.perform(get("/api/v1/patient-correction-requests").with(user(myUsername).roles("PATIENT")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/patient-correction-requests/" + myRequest + "/approve")
            .with(user(myUsername).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version", 0L))))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject(
        "select status from patient_correction_requests where id=?", String.class, myRequest))
        .isEqualTo("PENDING");
  }

  private long createPatient(String first, String last, String address, String phone) throws Exception {
    String tag = UUID.randomUUID().toString();
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("patientIdentifier", "CORR-" + tag);
    body.put("firstName", first);
    body.put("lastName", last);
    body.put("dateOfBirth", "1985-05-05");
    body.put("address", address);
    if (phone != null) body.put("phoneNumber", phone);
    MvcResult result = mvc.perform(adminPost("/api/v1/patients", body))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private String createPatientAccount(long patientId) {
    String username = "corr_user_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.queryForObject("""
        insert into app_users(username,password_hash,role,enabled,email_verified,requested_role,patient_id)
        values (?, 'unused-test-password-hash', 'PATIENT', true, true, 'PATIENT', ?) returning id
        """, Long.class, username, patientId);
    return username;
  }

  private int auditCount(long entityId, String eventType) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where entity_id=? and event_type=?",
        Integer.class, entityId, eventType);
  }

  private String patientField(long patientId, String column) {
    return jdbc.queryForObject("select " + column + " from patients where id=?", String.class, patientId);
  }

  private JsonNode patientPost(String path, Object body, String username) throws Exception {
    MvcResult result = mvc.perform(post(path)
            .with(user(username).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private MvcResult patientPostExpect(int expected, String path, Object body, String username)
      throws Exception {
    return mvc.perform(post(path)
            .with(user(username).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn();
  }

  private JsonNode staffGet(String path, String username) throws Exception {
    return json.readTree(mvc.perform(get(path).with(user(username)).with(csrf())
            .header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder adminPost(String path, Object body) throws Exception {
    return post(path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }
}
