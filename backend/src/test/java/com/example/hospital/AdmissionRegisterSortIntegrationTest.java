package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.service.AdmissionSort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
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
 * Sortable admission-register columns (#164).
 */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class AdmissionRegisterSortIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private long doctorId() {
    return jdbc.queryForObject("select id from doctors where department_id=1 order by id limit 1",
        Long.class);
  }

  private long patient(String first, String last) {
    return jdbc.queryForObject("""
        insert into patients(department_id,patient_identifier,first_name,last_name,date_of_birth,created_at,updated_at)
        values (1,?,?,?,?,now(),now()) returning id
        """, Long.class, "SORT-" + UUID.randomUUID().toString().substring(0, 8),
        first, last, Date.valueOf("1980-01-01"));
  }

  private long admit(long patientId, String status, String number, String roomNumber) {
    long id = jdbc.queryForObject("""
        insert into admissions(department_id,patient_id,attending_doctor_id,admission_number,
          admission_date_time,status,created_by,created_at,updated_at)
        values (1,?,?,?,now(),?,(select id from app_users where username='admin'),now(),now())
        returning id
        """, Long.class, patientId, doctorId(), number, status);
    if (roomNumber != null) {
      long roomId = jdbc.queryForObject("""
          insert into rooms(department_id,room_number,bed_count,active,created_at,updated_at)
          values (1,?,1,true,now(),now()) returning id
          """, Long.class, roomNumber);
      jdbc.update("""
          insert into room_assignments(department_id,admission_id,room_id,assigned_at,created_by,created_at,updated_at)
          values (1,?,?,now(),(select id from app_users where username='admin'),now(),now())
          """, id, roomId);
    }
    return id;
  }

  private JsonNode register(String... params) throws Exception {
    return registerResponse(params).body;
  }

  private record RegisterResponse(JsonNode body, String appliedSort, String totalCount) {}

  /**
   * Fetches the register, keeping the response as well as its body. The applied sort
   * is echoed in a header, not the body: adding fields to the body would stop the
   * PageLinks advice from recognising the PagedResult and the endpoint would quietly
   * lose the Link and X-Total-Count headers every other list returns.
   */
  private RegisterResponse registerResponse(String... params) throws Exception {
    var request = get("/api/v1/admissions").param("size", "200");
    for (String param : params) {
      int split = param.indexOf('=');
      request = request.param(param.substring(0, split), param.substring(split + 1));
    }
    var response = mvc.perform(request.with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk()).andReturn().getResponse();
    return new RegisterResponse(
        json.readTree(response.getContentAsString()),
        response.getHeader("X-Applied-Sort"),
        response.getHeader("X-Total-Count"));
  }

  private List<String> lastNames(JsonNode page) {
    var out = new ArrayList<String>();
    for (JsonNode row : page.path("items")) out.add(row.path("patient").path("lastName").asText());
    return out;
  }

  /**
   * The register's room column: the current assignment's room. The response nests
   * the room number under "rooms" rather than on the assignment, so read it there.
   */
  private List<String> roomNumbers(JsonNode page) {
    var out = new ArrayList<String>();
    for (JsonNode row : page.path("items")) {
      String number = "";
      for (JsonNode entry : row.path("rooms")) {
        JsonNode room = entry.path("room");
        JsonNode assignment = entry.path("assignment");
        if (assignment.path("releasedAt").isNull() || assignment.path("releasedAt").isMissingNode()) {
          number = room.path("roomNumber").asText();
          break;
        }
      }
      out.add(number);
    }
    return out;
  }

  @Test
  void sortsByPatientRoomAndStatusWhileKeepingTheActiveFilter() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    // Distinct first letters so the expected order is unambiguous.
    long zoe = patient("Zoe", "Sort" + tag);
    long adam = patient("Adam", "Sort" + tag);
    admit(zoe, "ACTIVE", "ADM-SORT-A-" + tag, "R" + tag + "9");
    admit(adam, "DISCHARGED", "ADM-SORT-B-" + tag, "R" + tag + "1");

    // ADM-SORT-A is Zoe's and ADM-SORT-B is Adam's. Both share a last name, so the
    // first name decides: Adam before Zoe, so B before A.
    JsonNode byName = register("sort=patient:asc");
    List<String> numbers = new ArrayList<>();
    for (JsonNode row : byName.path("items")) {
      numbers.add(row.path("admission").path("admissionNumber").asText());
    }
    assertThat(numbers).contains("ADM-SORT-A-" + tag, "ADM-SORT-B-" + tag);
    assertThat(numbers.indexOf("ADM-SORT-B-" + tag))
        .as("Adam before Zoe when sorted by patient name ascending")
        .isLessThan(numbers.indexOf("ADM-SORT-A-" + tag));
    // Descending reverses it.
    List<String> reversed = new ArrayList<>();
    for (JsonNode row : register("sort=patient:desc").path("items")) {
      reversed.add(row.path("admission").path("admissionNumber").asText());
    }
    assertThat(reversed.indexOf("ADM-SORT-A-" + tag))
        .as("Zoe before Adam when sorted descending")
        .isLessThan(reversed.indexOf("ADM-SORT-B-" + tag));

    // By room, the two rooms are ordered relative to each other within this page.
    // Only our two rooms are compared, so seeded rows and room-less admissions in
    // the same page cannot affect the expectation.
    List<String> rooms = roomNumbers(register("sort=room:asc", "search=ADM-SORT-"));
    assertThat(rooms).containsExactlyInAnyOrder("R" + tag + "1", "R" + tag + "9");
    assertThat(rooms.indexOf("R" + tag + "1"))
        .as("room sort orders R...1 before R...9")
        .isLessThan(rooms.indexOf("R" + tag + "9"));

    // The sort retains the status filter: only ACTIVE rows.
    JsonNode active = register("sort=room:asc", "status=ACTIVE");
    long activeRows = active.path("totalElements").asLong();
    long ourActive = 0, ourDischarged = 0;
    for (JsonNode row : active.path("items")) {
      String number = row.path("admission").path("admissionNumber").asText();
      if (number.equals("ADM-SORT-A-" + tag)) ourActive++;
      if (number.equals("ADM-SORT-B-" + tag)) ourDischarged++;
    }
    assertThat(ourActive).isEqualTo(1);
    assertThat(ourDischarged).as("the status filter is retained alongside the sort").isZero();
    assertThat(activeRows).isPositive();
  }

  @Test
  void theResponseEchoesTheAppliedSortAndTheResultCount() throws Exception {
    // With no sort requested the server says which default it used.
    RegisterResponse defaulted = registerResponse();
    assertThat(defaulted.appliedSort()).isEqualTo("admissionDate:desc");
    assertThat(Long.parseLong(defaulted.totalCount()))
        .as("the header total agrees with the body total")
        .isEqualTo(defaulted.body().path("totalElements").asLong());

    // An explicit sort is echoed exactly.
    assertThat(registerResponse("sort=room:asc").appliedSort()).isEqualTo("room:asc");
    assertThat(registerResponse("sort=patient:desc").appliedSort()).isEqualTo("patient:desc");

    // A key with no direction takes that key's own default, not a blanket one.
    assertThat(registerResponse("sort=patient").appliedSort()).isEqualTo("patient:asc");
    assertThat(registerResponse("sort=admissionDate").appliedSort()).isEqualTo("admissionDate:desc");

    // The result count reflects the filtered set, not the whole table.
    String tag = UUID.randomUUID().toString().substring(0, 8);
    long p = patient("Counted", "Count" + tag);
    long admission = admit(p, "ACTIVE", "ADM-COUNT-" + tag, null);
    RegisterResponse filtered = registerResponse("search=ADM-COUNT-" + tag);
    assertThat(filtered.body().path("totalElements").asLong()).isEqualTo(1);
    assertThat(Long.parseLong(filtered.totalCount())).isEqualTo(1L);
    assertThat(admission).isPositive();
  }

  @Test
  void theSortableOptionsAreServedByTheServer() throws Exception {
    mvc.perform(get("/api/v1/admissions/sort-options")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sortable").isArray())
        .andExpect(jsonPath("$.sortable.length()").value(4))
        .andExpect(jsonPath("$.resultCountField").value("totalElements"));
  }

  @Test
  void anUnknownSortKeyOrDirectionIsRefusedNotIgnored() throws Exception {
    mvc.perform(get("/api/v1/admissions").param("sort", "password")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT"));

    mvc.perform(get("/api/v1/admissions").param("sort", "room:sideways")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT"));
  }

  @Test
  void admissionsWithNoCurrentRoomStillSortAndStillAppear() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    long p = patient("NoRoom", "Ward" + tag);
    long admission = admit(p, "ACTIVE", "ADM-NOROOM-" + tag, null);

    // Sorting by room must not drop a row whose room column is empty.
    JsonNode byRoom = register("sort=room:asc", "search=ADM-NOROOM-" + tag);
    assertThat(byRoom.path("totalElements").asLong()).isEqualTo(1);
    assertThat(roomNumbers(byRoom)).containsExactly("");
    assertThat(admission).isPositive();
  }

  @Test
  void theSortWhitelistIsClosedAndTheDefaultIsTheDate() {
    // The direction is carried as a bound boolean, so a caller's word is never
    // spliced into SQL; an unknown key is rejected by the service before any query.
    assertThat(AdmissionSort.parse("room:asc")).containsExactly("room", "asc");
    assertThat(AdmissionSort.parse("room")).containsExactly("room", "asc");
    assertThat(AdmissionSort.parse("admissionDate")).containsExactly("admissionDate", "desc");
    assertThat(AdmissionSort.parse(null)).containsExactly("admissionDate", "desc");
    assertThat(AdmissionSort.parse("")).containsExactly("admissionDate", "desc");
    assertThat(AdmissionSort.describe("room", "asc")).isEqualTo("room:asc");
    assertThat(AdmissionSort.describe(null, null)).isEqualTo("admissionDate:desc");
  }
}
