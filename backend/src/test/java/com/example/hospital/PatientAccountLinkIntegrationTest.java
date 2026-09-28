package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Administrator-reviewed linking of a self-registered portal account to the real
 * patient record (#160).
 */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class PatientAccountLinkIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void approvedLinkRepointsTheAccountAndKeepsBothActsAuditable() throws Exception {
    long realPatientId = createRealPatient("Ana", "Vasileva", "1980-04-04");
    long placeholderId = createPlaceholder("Ana", "Vasileva", "1980-04-04");
    long userId = createPatientAccount(placeholderId);

    // An unreviewed link must not change where the account points.
    assertThat(accountPatientId(userId)).isEqualTo(placeholderId);

    JsonNode requested = json.readTree(mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Date of birth and name match the chart.")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    assertThat(requested.path("status").asText()).isEqualTo("PENDING");
    assertThat(requested.path("placeholderPatientId").asLong()).isEqualTo(placeholderId);
    assertThat(requested.path("targetPatientId").asLong()).isEqualTo(realPatientId);
    assertThat(accountPatientId(userId)).isEqualTo(placeholderId);

    // The review queue surfaces the unresolved account for an administrator.
    JsonNode candidates = adminGet("/api/v1/patient-account-links/candidates");
    boolean listed = false;
    for (JsonNode row : candidates) {
      if (row.path("userId").asLong() == userId) {
        listed = true;
        assertThat(row.path("placeholderPatientId").asLong()).isEqualTo(placeholderId);
        assertThat(row.path("placeholderIdentifier").asText()).startsWith("SELF-");
        assertThat(row.path("linkStatus").asText()).isEqualTo("PENDING");
      }
    }
    assertThat(listed).as("unlinked self-registration appears in the review queue").isTrue();

    long linkId = requested.path("id").asLong();
    JsonNode approved = json.readTree(mvc.perform(adminPost(
            "/api/v1/patient-account-links/" + linkId + "/approve", Map.of("note", "Checked against the chart.")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(approved.path("status").asText()).isEqualTo("LINKED");

    // The account now reads the real record, and only once.
    assertThat(accountPatientId(userId)).isEqualTo(realPatientId);
    assertThat(jdbc.queryForObject(
        "select count(*) from app_users where patient_id=?", Integer.class, realPatientId))
        .isEqualTo(1);

    // The proposal and the decision are separately auditable.
    assertThat(auditCount(linkId, "PATIENT_ACCOUNT_LINK_REQUESTED")).isEqualTo(1);
    assertThat(auditCount(linkId, "PATIENT_ACCOUNT_LINKED")).isEqualTo(1);

    // A second decision on the same request is refused.
    mvc.perform(adminPost("/api/v1/patient-account-links/" + linkId + "/reject", Map.of()))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectedLinkLeavesTheAccountOnItsPlaceholder() throws Exception {
    long realPatientId = createRealPatient("Boris", "Iliev", "1975-07-07");
    long placeholderId = createPlaceholder("Boris", "Iliev", "1975-07-07");
    long userId = createPatientAccount(placeholderId);

    long linkId = json.readTree(mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Name only.")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .path("id").asLong();

    JsonNode rejected = json.readTree(mvc.perform(adminPost(
            "/api/v1/patient-account-links/" + linkId + "/reject",
            Map.of("note", "Name alone is not sufficient evidence.")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
    assertThat(accountPatientId(userId)).isEqualTo(placeholderId);
    assertThat(auditCount(linkId, "PATIENT_ACCOUNT_LINK_REJECTED")).isEqualTo(1);

    // A rejected request does not block a later, better-evidenced one.
    long second = json.readTree(mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Date of birth confirmed.")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .path("id").asLong();
    assertThat(second).isNotEqualTo(linkId);
  }

  @Test
  void aPlaceholderIsNeverRemovedWhenClinicalHistoryReferencesIt() throws Exception {
    long realPatientId = createRealPatient("Cvetana", "Petkova", "1990-02-02");
    long placeholderId = createPlaceholder("Cvetana", "Petkova", "1990-02-02");
    long admissionId = createAdmission(placeholderId);
    long userId = createPatientAccount(placeholderId);

    long linkId = json.readTree(mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Verified.")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .path("id").asLong();
    mvc.perform(adminPost("/api/v1/patient-account-links/" + linkId + "/approve", Map.of()))
        .andExpect(status().isOk());

    assertThat(accountPatientId(userId)).isEqualTo(realPatientId);
    // The admission still points at the placeholder, so the placeholder must survive:
    // the flow repoints the account, it never rewrites or removes clinical history.
    assertThat(jdbc.queryForObject("select count(*) from admissions where id=? and patient_id=?",
        Integer.class, admissionId, placeholderId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from patients where id=?", Integer.class, placeholderId))
        .isEqualTo(1);
  }

  @Test
  void duplicateLinksAndAlreadyOwnedTargetsAreRefused() throws Exception {
    long realPatientId = createRealPatient("Dimitar", "Yankov", "1983-09-09");
    long otherRealId = createRealPatient("Elena", "Maneva", "1988-01-01");
    long placeholderId = createPlaceholder("Dimitar", "Yankov", "1983-09-09");
    long userId = createPatientAccount(placeholderId);

    // Linking an account to its own placeholder would be a no-op that hides a duplicate.
    MvcResult same = mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", placeholderId, "evidence", "Self.")))
        .andExpect(status().isBadRequest()).andReturn();
    assertThat(json.readTree(same.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("SAME_RECORD");

    // A second open request for the same account is refused.
    mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "First.")))
        .andExpect(status().isCreated());
    MvcResult duplicate = mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", otherRealId, "evidence", "Second.")))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(duplicate.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("LINK_ALREADY_OPEN");

    // A target another account already owns is refused, so linking cannot steal a record.
    long secondUserId = createPatientAccount(createPlaceholder("Someone", "Else", "1970-01-01"));
    mvc.perform(adminPost("/api/v1/patient-account-links/" + secondUserId,
            Map.of("targetPatientId", realPatientId, "evidence", "Attempt.")))
        .andExpect(status().isCreated());
    long secondLink = jdbc.queryForObject(
        "select id from patient_account_links where user_id=? and status='PENDING'",
        Long.class, secondUserId);
    mvc.perform(adminPost("/api/v1/patient-account-links/" + secondLink + "/approve", Map.of()))
        .andExpect(status().isOk());

    long thirdUserId = createPatientAccount(createPlaceholder("Third", "Party", "1971-02-02"));
    MvcResult stealing = mvc.perform(adminPost("/api/v1/patient-account-links/" + thirdUserId,
            Map.of("targetPatientId", realPatientId, "evidence", "Not mine.")))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(stealing.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("TARGET_ALREADY_LINKED");
  }

  @Test
  void approvalRefusesWhenTheAccountMovedAfterTheRequestWasRaised() throws Exception {
    long realPatientId = createRealPatient("Georgi", "Todorov", "1979-05-05");
    long placeholderId = createPlaceholder("Georgi", "Todorov", "1979-05-05");
    long userId = createPatientAccount(placeholderId);
    long linkId = json.readTree(mvc.perform(adminPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Verified.")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .path("id").asLong();

    // Someone repoints the account before the request is decided.
    long otherRealId = createRealPatient("Other", "Record", "1970-03-03");
    jdbc.update("update app_users set patient_id=? where id=?", otherRealId, userId);

    MvcResult conflict = mvc.perform(adminPost("/api/v1/patient-account-links/" + linkId + "/approve",
            Map.of()))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(conflict.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("ACCOUNT_MOVED");
    assertThat(accountPatientId(userId)).isEqualTo(otherRealId);
    assertThat(jdbc.queryForObject(
        "select status from patient_account_links where id=?", String.class, linkId))
        .isEqualTo("PENDING");
  }

  @Test
  void onlyAdministratorsMayReviewOrRequestLinks() throws Exception {
    long realPatientId = createRealPatient("Hristina", "Vasileva", "1986-06-06");
    long placeholderId = createPlaceholder("Hristina", "Vasileva", "1986-06-06");
    long userId = createPatientAccount(placeholderId);
    long patientAccountId = createPatientAccount(createPlaceholder("Portal", "Owner", "1999-09-09"));
    String patientUsername = jdbc.queryForObject(
        "select username from app_users where id=?", String.class, patientAccountId);

    // Staff are not administrators: this flow needs the narrower permission.
    mvc.perform(staffPost("/api/v1/patient-account-links/" + userId,
            Map.of("targetPatientId", realPatientId, "evidence", "Name matches.")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/patient-account-links/candidates")
            .with(user("staff")).header("X-Department-Id", "1"))
        .andExpect(status().isForbidden());

    // Nor can a patient touch it.
    mvc.perform(post("/api/v1/patient-account-links/" + userId)
            .with(user(patientUsername).roles("PATIENT")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of(
                "targetPatientId", realPatientId, "evidence", "Mine."))))
        .andExpect(status().isForbidden());

    // Nothing was written by the refused attempts.
    assertThat(jdbc.queryForObject(
        "select count(*) from patient_account_links where user_id=?", Integer.class, userId)).isZero();
  }

  private long createRealPatient(String first, String last, String dob) throws Exception {
    String tag = UUID.randomUUID().toString();
    MvcResult result = mvc.perform(adminPost("/api/v1/patients", Map.of(
            "patientIdentifier", "LINK-REAL-" + tag,
            "firstName", first, "lastName", last, "dateOfBirth", dob)))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  /** A registration-shaped placeholder, the way RegistrationService creates one. */
  private long createPlaceholder(String first, String last, String dob) {
    return jdbc.queryForObject("""
        insert into patients(department_id,patient_identifier,first_name,last_name,date_of_birth,created_at,updated_at)
        values (1,?,?,?,?,now(),now()) returning id
        """, Long.class, "SELF-" + UUID.randomUUID(), first, last, java.sql.Date.valueOf(dob));
  }

  private long createPatientAccount(long placeholderId) {
    String username = "link_user_" + UUID.randomUUID().toString().replace("-", "");
    return jdbc.queryForObject("""
        insert into app_users(username,password_hash,role,enabled,email_verified,requested_role,patient_id)
        values (?, 'unused-test-password-hash', 'PATIENT', true, true, 'PATIENT', ?) returning id
        """, Long.class, username, placeholderId);
  }

  private long createAdmission(long patientId) {
    Long doctorId = jdbc.queryForObject("select id from doctors where department_id=1 order by id limit 1",
        Long.class);
    Long adminId = jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
    return jdbc.queryForObject("""
        insert into admissions(department_id,patient_id,attending_doctor_id,admission_number,
          admission_date_time,status,created_by,created_at,updated_at)
        values (1,?,?,?,now(),'ACTIVE',?,now(),now()) returning id
        """, Long.class, patientId, doctorId, "ADM-" + UUID.randomUUID(), adminId);
  }

  private long accountPatientId(long userId) {
    return jdbc.queryForObject("select patient_id from app_users where id=?", Long.class, userId);
  }

  private int auditCount(long entityId, String eventType) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where entity_id=? and event_type=?",
        Integer.class, entityId, eventType);
  }

  private JsonNode adminGet(String path) throws Exception {
    return json.readTree(mvc.perform(get(path).with(user("admin")).with(csrf())
            .header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder adminPost(String path, Object body) throws Exception {
    return post(path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }

  private MockHttpServletRequestBuilder staffPost(String path, Object body) throws Exception {
    return post(path).with(user("staff")).with(csrf()).header("X-Department-Id", "1")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }
}
