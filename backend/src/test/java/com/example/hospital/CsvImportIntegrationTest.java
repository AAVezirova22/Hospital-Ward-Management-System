package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Previewed CSV import for initial setup (#161).
 */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class CsvImportIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private static final String PATIENT_HEADER =
      "patientIdentifier,firstName,lastName,dateOfBirth,address,phoneNumber";

  @Test
  void previewWritesNothingAndCommitCreatesTheReviewedRows() throws Exception {
    String tag = shortTag();
    String csv = PATIENT_HEADER + "\n"
        + "IMP-" + tag + "-1,Ivan,Stoyanov,1980-01-31,Sofia,+359888123456\n"
        + "IMP-" + tag + "-2,Maria,Georgieva,1975-06-15,Plovdiv,+359888123457\n";

    JsonNode preview = adminPreview("PATIENT", csv, "SKIP");
    long batchId = preview.path("id").asLong();
    assertThat(preview.path("status").asText()).isEqualTo("PREVIEWED");
    assertThat(preview.path("ready").asInt()).isEqualTo(2);
    assertThat(preview.path("invalid").asInt()).isZero();
    assertThat(preview.path("skippedDuplicates").asInt()).isZero();
    assertThat(preview.path("rows")).hasSize(2);
    // Each row carries the verdict, so a reviewer can see what would happen.
    assertThat(preview.path("rows").get(0).path("status").asText()).isEqualTo("READY");
    assertThat(preview.path("rows").get(0).path("problems").isNull()).isTrue();

    // The whole point: preview is not a write.
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-1")).isZero();
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-2")).isZero();

    JsonNode committed = json.readTree(mvc.perform(adminPost("/api/v1/csv-imports/" + batchId + "/commit"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(committed.path("status").asText()).isEqualTo("COMMITTED");
    assertThat(committed.path("created").asInt()).isEqualTo(2);
    assertThat(committed.path("skipped").asInt()).isZero();

    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-1")).isEqualTo(1);
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-2")).isEqualTo(1);
    // The staged rows now record which record each one produced.
    assertThat(jdbc.queryForObject(
        "select count(*) from csv_import_rows where batch_id=? and created_record_id is not null",
        Integer.class, batchId)).isEqualTo(2);
    assertThat(auditCount(batchId, "CSV_IMPORT_PREVIEWED")).isEqualTo(1);
    assertThat(auditCount(batchId, "CSV_IMPORT_COMMITTED")).isEqualTo(1);
  }

  @Test
  void invalidRowsAreReportedAndNeverCommitted() throws Exception {
    String tag = shortTag();
    String csv = PATIENT_HEADER + "\n"
        + "IMP-" + tag + "-ok,Ana,Popova,1980-01-01,Sofia,\n"
        + "IMP-" + tag + "-baddate,Bo,Peev,not-a-date,Sofia,\n"
        + "IMP-" + tag + "-future,Cy,Rusev,2999-01-01,Sofia,\n"
        + "IMP-" + tag + "-badphone,Di,Minev,1980-01-01,Sofia,12345\n"
        + ",Ed,NoFirstName,1980-01-01,Sofia,\n"
        + "too,few,columns\n";

    JsonNode preview = adminPreview("PATIENT", csv, "SKIP");
    assertThat(preview.path("ready").asInt()).isEqualTo(1);
    assertThat(preview.path("invalid").asInt()).isEqualTo(5);

    // The problems are specific enough to act on.
    String problems = "";
    for (JsonNode row : preview.path("rows")) {
      if (row.path("rowNumber").asInt() == 2) problems = row.path("problems").asText();
    }
    assertThat(problems).contains("dateOfBirth must be an ISO date");
    String future = "";
    for (JsonNode row : preview.path("rows")) {
      if (row.path("rowNumber").asInt() == 3) future = row.path("problems").asText();
    }
    assertThat(future).contains("must be in the past");
    String phone = "";
    for (JsonNode row : preview.path("rows")) {
      if (row.path("rowNumber").asInt() == 4) phone = row.path("problems").asText();
    }
    assertThat(phone).contains("E.164");
    String columns = "";
    for (JsonNode row : preview.path("rows")) {
      if (row.path("rowNumber").asInt() == 6) columns = row.path("problems").asText();
    }
    assertThat(columns).contains("Expected 6 columns, found 3");

    mvc.perform(adminPost("/api/v1/csv-imports/" + preview.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    // The one valid row landed; the five invalid ones did not.
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-ok")).isEqualTo(1);
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-baddate")).isZero();
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-badphone")).isZero();
  }

  @Test
  void duplicatesAreSkippedByDefaultAndRejectedWhenAsked() throws Exception {
    String tag = shortTag();
    String existing = "IMP-" + tag + "-DUP";
    mvc.perform(adminPost("/api/v1/patients", Map.of(
            "patientIdentifier", existing, "firstName", "Dup", "lastName", "Licate",
            "dateOfBirth", "1980-01-01", "address", "Sofia", "phoneNumber", "+359888000000")))
        .andExpect(status().isCreated());
    String csv = PATIENT_HEADER + "\n"
        + existing + ",Someone,Else,1980-01-01,Sofia,\n"
        + "IMP-" + tag + "-NEW,Nova,Record,1981-02-02,Sofia,\n";

    JsonNode skipped = adminPreview("PATIENT", csv, "SKIP");
    assertThat(skipped.path("skippedDuplicates").asInt()).isEqualTo(1);
    assertThat(skipped.path("ready").asInt()).isEqualTo(1);
    mvc.perform(adminPost("/api/v1/csv-imports/" + skipped.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-NEW")).isEqualTo(1);
    // Still exactly one: the duplicate row was skipped, not upserted.
    assertThat(count("patients", "patient_identifier", existing)).isEqualTo(1);

    // Under REJECT the duplicate makes its own row invalid instead. The other row
    // is a duplicate by now too, because the SKIP batch above already created it —
    // so this batch has no creatable rows at all.
    JsonNode rejected = adminPreview("PATIENT", csv, "REJECT");
    assertThat(rejected.path("invalid").asInt()).isEqualTo(2);
    assertThat(rejected.path("ready").asInt()).isZero();
    assertThat(rejected.path("skippedDuplicates").asInt()).isZero();
    String reason = "";
    for (JsonNode row : rejected.path("rows")) {
      if (row.path("rowNumber").asInt() == 1) reason = row.path("problems").asText();
    }
    assertThat(reason).contains("rejects duplicates");

    // A REJECT batch refuses to commit, so the count cannot move.
    mvc.perform(adminPost("/api/v1/csv-imports/" + rejected.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-NEW")).isEqualTo(1);
  }

  @Test
  void aBatchCommitsAtMostOnceAndDiscardBlocksAnyCommit() throws Exception {
    String tag = shortTag();
    String csv = PATIENT_HEADER + "\n" + "IMP-" + tag + "-ONE,Once,Only,1980-01-01,Sofia,\n";

    JsonNode preview = adminPreview("PATIENT", csv, "SKIP");
    long batchId = preview.path("id").asLong();
    mvc.perform(adminPost("/api/v1/csv-imports/" + batchId + "/commit"))
        .andExpect(status().isOk());
    // A double-clicked commit must not import twice.
    MvcResult second = mvc.perform(adminPost("/api/v1/csv-imports/" + batchId + "/commit"))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(second.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("ALREADY_COMMITTED");
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-ONE")).isEqualTo(1);

    // A discarded batch can never be committed.
    long discardable = adminPreview("PATIENT", csv.replace("ONE", "TWO"), "SKIP").path("id").asLong();
    mvc.perform(adminPost("/api/v1/csv-imports/" + discardable + "/discard"))
        .andExpect(status().isOk());
    MvcResult afterDiscard = mvc.perform(adminPost("/api/v1/csv-imports/" + discardable + "/commit"))
        .andExpect(status().isConflict()).andReturn();
    assertThat(json.readTree(afterDiscard.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("NOT_COMMITTABLE");
    assertThat(count("patients", "patient_identifier", "IMP-" + tag + "-TWO")).isZero();
  }

  @Test
  void everyEntityTypeImportsAndOnlyWithinTheReviewersDepartment() throws Exception {
    String tag = shortTag();

    JsonNode doctors = adminPreview("DOCTOR",
        "doctorIdentifier,firstName,lastName,specialty\n"
            + "DOC-" + tag + ",Ada,Lovelace,Cardiology\n", "SKIP");
    mvc.perform(adminPost("/api/v1/csv-imports/" + doctors.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    assertThat(count("doctors", "doctor_identifier", "DOC-" + tag)).isEqualTo(1);

    JsonNode rooms = adminPreview("ROOM", "roomNumber,bedCount\n" + "RM-" + tag + ",4\n", "SKIP");
    mvc.perform(adminPost("/api/v1/csv-imports/" + rooms.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    assertThat(count("rooms", "room_number", "RM-" + tag)).isEqualTo(1);

    JsonNode procedures = adminPreview("PROCEDURE",
        "procedureCode,procedureName,currentCost\nPR-" + tag + ",Minor procedure,12.50\n", "SKIP");
    mvc.perform(adminPost("/api/v1/csv-imports/" + procedures.path("id").asLong() + "/commit"))
        .andExpect(status().isOk());
    assertThat(count("medical_procedures", "procedure_code", "PR-" + tag)).isEqualTo(1);

    // A batch in another department is invisible, not merely forbidden.
    long foreignHospital = jdbc.queryForObject(
        "insert into hospitals(name,join_code) values ('Other hospital','H-FOREIGN') returning id",
        Long.class);
    long foreignDepartment = jdbc.queryForObject(
        "insert into departments(hospital_id,name,join_code) values (?, 'Other department','D-FOREIGN')"
            + " returning id", Long.class, foreignHospital);
    long foreign = jdbc.queryForObject(
        "insert into csv_import_batches(department_id,entity_type,file_name,total_rows,created_by)"
            + " values (?,'PATIENT','x.csv',1,1) returning id", Long.class, foreignDepartment);
    mvc.perform(get("/api/v1/csv-imports/" + foreign)
            .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isNotFound());
  }

  @Test
  void malformedFilesAreRefusedBeforeAnythingIsStaged() throws Exception {
    // Wrong header for the chosen type.
    MvcResult header = mvc.perform(importFile("PATIENT", "roomNumber,bedCount\nRM-1,2\n"))
        .andExpect(status().isBadRequest()).andReturn();
    assertThat(json.readTree(header.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("UNEXPECTED_HEADER");

    // Unsupported entity and unsupported duplicate policy.
    MvcResult entity = mvc.perform(importFile("BEDS", PATIENT_HEADER + "\n"))
        .andExpect(status().isBadRequest()).andReturn();
    assertThat(json.readTree(entity.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("UNSUPPORTED_ENTITY");

    MvcResult policy = mvc.perform(multipart("/api/v1/csv-imports/preview")
            .file(new MockMultipartFile("file", "p.csv", "text/csv",
                (PATIENT_HEADER + "\nA,B,1980-01-01,x,\n").getBytes(StandardCharsets.UTF_8)))
            .param("entityType", "PATIENT").param("duplicatePolicy", "MERGE")
            .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest()).andReturn();
    assertThat(json.readTree(policy.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("UNSUPPORTED_POLICY");

    // Header only, and no file at all.
    mvc.perform(importFile("PATIENT", PATIENT_HEADER + "\n"))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/v1/csv-imports/preview")
            .param("entityType", "PATIENT")
            .with(user("admin")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest());

    // None of the refused attempts left a batch behind.
    assertThat(jdbc.queryForObject(
        "select count(*) from csv_import_batches where file_name='p.csv'", Integer.class)).isZero();
  }

  @Test
  void onlyAdministratorsMayPreviewOrCommit() throws Exception {
    String csv = PATIENT_HEADER + "\nIMP-NOPE,Sneaky,Attempt,1980-01-01,Sofia,\n";
    mvc.perform(multipart("/api/v1/csv-imports/preview")
            .file(new MockMultipartFile("file", "n.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
            .param("entityType", "PATIENT")
            .with(user("staff")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/csv-imports")
            .with(user("staff")).header("X-Department-Id", "1"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/csv-imports/1/commit")
            .with(user("staff")).with(csrf()).header("X-Department-Id", "1"))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject(
        "select count(*) from csv_import_batches where file_name='n.csv'", Integer.class)).isZero();
  }

  private int count(String table, String column, String value) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + "=?", Integer.class, value);
  }

  private int auditCount(long entityId, String eventType) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where entity_id=? and event_type=?",
        Integer.class, entityId, eventType);
  }

  private static String shortTag() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private JsonNode adminPreview(String entityType, String csv, String policy) throws Exception {
    return json.readTree(mvc.perform(importFile(entityType, csv).param("duplicatePolicy", policy))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  }

  private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
      importFile(String entityType, String csv) {
    return multipart("/api/v1/csv-imports/preview")
        .file(new MockMultipartFile("file", "import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
        .param("entityType", entityType)
        .with(user("admin")).with(csrf()).header("X-Department-Id", "1");
  }

  private MockHttpServletRequestBuilder adminPost(String path, Object body) throws Exception {
    return post(path).with(user("admin")).with(csrf()).header("X-Department-Id", "1")
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
  }

  private MockHttpServletRequestBuilder adminPost(String path) {
    return post(path).with(user("admin")).with(csrf()).header("X-Department-Id", "1");
  }
}
