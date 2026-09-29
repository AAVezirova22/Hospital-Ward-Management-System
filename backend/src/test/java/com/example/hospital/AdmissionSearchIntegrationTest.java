package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Search and status filters on the admissions list (#163).
 */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class AdmissionSearchIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private long departmentId() {
    return jdbc.queryForObject(
        "select id from departments where join_code is not null order by id limit 1", Long.class);
  }

  private long doctorId() {
    return jdbc.queryForObject("select id from doctors where department_id=1 order by id limit 1",
        Long.class);
  }

  private long patient(String first, String last, String identifier) {
    return jdbc.queryForObject("""
        insert into patients(department_id,patient_identifier,first_name,last_name,date_of_birth,created_at,updated_at)
        values (1,?,?,?,?,now(),now()) returning id
        """, Long.class, identifier, first, last, Date.valueOf("1980-01-01"));
  }

  private long admit(long patientId, String status, String number) {
    return jdbc.queryForObject("""
        insert into admissions(department_id,patient_id,attending_doctor_id,admission_number,
          admission_date_time,status,created_by,created_at,updated_at)
        values (1,?,?,?,now(),?,(select id from app_users where username='admin'),now(),now())
        returning id
        """, Long.class, patientId, doctorId(), number, status);
  }

  private JsonNode search(String query) throws Exception {
    return json.readTree(mvc.perform(get("/api/v1/admissions").param("search", query)
            .param("size", "100")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  /** The page body nests the admission under "admission" inside each item. */
  private boolean contains(JsonNode page, String admissionNumber) {
    for (JsonNode row : items(page)) {
      if (admissionNumber.equals(row.path("admission").path("admissionNumber").asText()))
        return true;
    }
    return false;
  }

  private JsonNode items(JsonNode page) {
    return page.path("items");
  }

  @Test
  void searchFindsByAdmissionNumberPatientNameAndIdentifier() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    long p = patient("Radomir", "Krastev", "SRCH-" + tag);
    long hit = admit(p, "ACTIVE", "ADM-SEARCH-" + tag);
    long other = admit(patient("Someone", "Else", "OTHER-" + tag), "ACTIVE", "ADM-OTHER-" + tag);

    // By admission number.
    JsonNode byNumber = search("ADM-SEARCH-" + tag);
    assertThat(contains(byNumber, "ADM-SEARCH-" + tag)).isTrue();
    assertThat(contains(byNumber, "ADM-OTHER-" + tag)).isFalse();

    // By patient identifier.
    assertThat(contains(search("SRCH-" + tag), "ADM-SEARCH-" + tag)).isTrue();

    // By first name, last name, and both together.
    assertThat(contains(search("Radomir"), "ADM-SEARCH-" + tag)).isTrue();
    assertThat(contains(search("Krastev"), "ADM-SEARCH-" + tag)).isTrue();
    assertThat(contains(search("Radomir Krastev"), "ADM-SEARCH-" + tag)).isTrue();

    // Case-insensitive, as a user typing in a hurry expects.
    assertThat(contains(search("rAdOmIr"), "ADM-SEARCH-" + tag)).isTrue();

    // A term matching nothing returns an empty page rather than everything.
    assertThat(items(search("zzz-no-such-patient-" + tag))).isEmpty();

    assertThat(hit).isNotEqualTo(other);
  }

  @Test
  void searchAndStatusFiltersCombine() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    long p = patient("Vanya", "Murdarova", "COMB-" + tag);
    long active = admit(p, "ACTIVE", "ADM-COMB-A-" + tag);
    long discharged = admit(patient("Vanya", "Murdarova", "COMB2-" + tag), "DISCHARGED",
        "ADM-COMB-D-" + tag);

    JsonNode both = json.readTree(mvc.perform(get("/api/v1/admissions")
            .param("search", "Vanya").param("status", "DISCHARGED").param("size", "100")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(contains(both, "ADM-COMB-D-" + tag)).isTrue();
    assertThat(contains(both, "ADM-COMB-A-" + tag)).isFalse();

    // Status alone still works, and an unknown status is refused.
    mvc.perform(get("/api/v1/admissions").param("status", "ACTIVE")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admissions").param("status", "NONSENSE")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest());

    assertThat(active).isNotEqualTo(discharged);
  }

  @Test
  void aBlankOrWildcardSearchDoesNotMatchEverything() throws Exception {
    long total = jdbc.queryForObject("select count(*) from admissions where department_id=1",
        Long.class);
    long matches = items(search("")).size();
    // An empty box means "no filter", so it returns the whole list, not nothing.
    assertThat(matches).isEqualTo((int) total);

    // A bare % must be a literal, not a wildcard. No admission number, patient name
    // or identifier contains a % or an underscore, so an escaped literal must match
    // nothing at all; an unescaped one would return the whole list.
    assertThat(items(search("%")))
        .as("a literal %% should match no admission, not every one")
        .isEmpty();
    assertThat(items(search("_")))
        .as("a literal _ should match no admission, not every one")
        .isEmpty();
    // And a term that genuinely is a prefix of a real number still works, proving
    // the escaping did not break ordinary search.
    String prefix = "ADM-SEARCH-";
    assertThat(jdbc.queryForObject(
        "select count(*) from admissions where admission_number like ?",
        Long.class, prefix + "%")).isPositive();
  }

  @Test
  void searchIsBoundedAndNeverLeaksAnotherDepartment() throws Exception {
    mvc.perform(get("/api/v1/admissions").param("search", "x".repeat(101))
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest());

    // A patient in a different department must not surface.
    long hospitalId = jdbc.queryForObject("select id from hospitals order by id limit 1", Long.class);
    long otherDepartment = jdbc.queryForObject(
        "insert into departments(hospital_id,name,join_code) values (?, 'Other ward','D-OTHER')"
            + " returning id", Long.class, hospitalId);
    long hidden = jdbc.queryForObject("""
        insert into patients(department_id,patient_identifier,first_name,last_name,date_of_birth,created_at,updated_at)
        values (?,?,?,?,?,now(),now()) returning id
        """, Long.class, otherDepartment, "LEAK-" + UUID.randomUUID().toString().substring(0, 8),
        "Hidden", "Patient", Date.valueOf("1980-01-01"));
    jdbc.queryForObject("""
        insert into admissions(department_id,patient_id,attending_doctor_id,admission_number,
          admission_date_time,status,created_by,created_at,updated_at)
        values (?,?,?,?,now(),'ACTIVE',(select id from app_users where username='admin'),now(),now())
        returning id
        """, Long.class, otherDepartment, hidden, 1L,
        "ADM-LEAK-" + UUID.randomUUID().toString().substring(0, 8));

    JsonNode result = search("LEAK-");
    for (JsonNode row : items(result)) {
      assertThat(row.path("admission").path("admissionNumber").asText()).doesNotStartWith("ADM-LEAK-");
    }
  }

  @Test
  void clearFiltersIsServedByTheServerAndResetsTheList() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    long p = patient("Clear", "Test", "CLR-" + tag);
    long admission = admit(p, "DISCHARGED", "ADM-CLR-" + tag);

    // The server defines what "no filters" means, so a client need not hard-code it.
    mvc.perform(get("/api/v1/admissions/filters")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.search").value(""))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.page").value(0));

    // Applying the cleared set returns the unfiltered list, which includes our row.
    JsonNode cleared = json.readTree(mvc.perform(get("/api/v1/admissions")
            .param("size", "100")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(contains(cleared, "ADM-CLR-" + tag)).isTrue();
    assertThat(admission).isPositive();
  }
}
