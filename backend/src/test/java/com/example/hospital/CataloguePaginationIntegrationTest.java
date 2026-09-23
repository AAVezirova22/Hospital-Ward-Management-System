package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CataloguePaginationIntegrationTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void doctorsAndProceduresSupportBoundedSearchAndStablePages() throws Exception {
    String marker = "catalogue-" + unique();
    createDoctor(marker, "Zoe", "Zulu", true);
    createDoctor(marker, "Ada", "Alpha", true);
    createDoctor(marker, "Inactive", "Alpha", false);

    JsonNode doctors =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/doctors?q=" + marker + "&active=true&page=0&size=1",
                null),
            200);
    assertThat(doctors.get("items").size()).isEqualTo(1);
    assertThat(doctors.get("items").get(0).get("lastName").asText()).isEqualTo("Alpha");
    assertThat(doctors.get("totalElements").asLong()).isEqualTo(2);
    assertThat(doctors.get("hasNext").asBoolean()).isTrue();
    assertThat(doctors.get("nextPage").asInt()).isEqualTo(1);

    JsonNode inactiveDoctors =
        result(
            request("admin", "GET", "/api/v1/doctors?q=" + marker + "&active=false", null),
            200);
    assertThat(inactiveDoctors.get("items").size()).isEqualTo(1);
    assertThat(inactiveDoctors.get("items").get(0).get("active").asBoolean()).isFalse();

    createProcedure(marker, "Zulu Procedure", true);
    createProcedure(marker, "Alpha Procedure", false);
    JsonNode procedures =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/procedures?q=" + marker + "&active=true&size=500",
                null),
            200);
    assertThat(procedures.get("size").asInt()).isEqualTo(100);
    assertThat(procedures.get("items").size()).isEqualTo(1);
    assertThat(procedures.get("items").get(0).get("procedureName").asText())
        .isEqualTo("Zulu Procedure");
  }

  @Test
  void roomsRetainCapacityAndActiveFiltersWithPagedOccupancy() throws Exception {
    String marker = "capacity-" + unique();
    JsonNode occupiedRoom = createRoom(marker + "-occupied", 2, true);
    occupyRoom(occupiedRoom);
    JsonNode availableRoom = createRoom(marker + "-available", 2, true);
    createRoom(marker + "-inactive", 2, false);

    JsonNode available =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?q=" + marker + "&active=true&minFree=1&size=20",
                null),
            200);
    assertThat(available.get("totalElements").asLong()).isEqualTo(2);
    JsonNode occupiedItem =
        findItem(available, occupiedRoom.get("id").asLong());
    assertThat(occupiedItem.get("occupiedBeds").asLong()).isEqualTo(1);
    assertThat(occupiedItem.get("availableBeds").asInt()).isEqualTo(1);
    JsonNode availableItem =
        findItem(available, availableRoom.get("id").asLong());
    assertThat(availableItem.get("occupiedBeds").asLong()).isZero();
    assertThat(availableItem.get("availableBeds").asInt()).isEqualTo(2);

    JsonNode selectedRoom =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?roomId=" + availableRoom.get("id").asLong(),
                null),
            200);
    assertThat(selectedRoom.get("items").size()).isEqualTo(1);
    assertThat(selectedRoom.get("items").get(0).get("id").asLong())
        .isEqualTo(availableRoom.get("id").asLong());

    JsonNode fullCapacityFilter =
        result(
            request("admin", "GET", "/api/v1/rooms?q=" + marker + "&minFree=2", null),
            200);
    assertThat(fullCapacityFilter.get("totalElements").asLong()).isEqualTo(1);

    JsonNode tooFewBeds =
        result(
            request("admin", "GET", "/api/v1/rooms?q=" + marker + "&minFree=3", null),
            200);
    assertThat(tooFewBeds.get("totalElements").asLong()).isZero();

    JsonNode inactive =
        result(
            request("admin", "GET", "/api/v1/rooms?q=" + marker + "&active=false", null),
            200);
    assertThat(inactive.get("items").size()).isEqualTo(1);
    assertThat(inactive.get("items").get(0).get("availableBeds").asInt()).isZero();
    JsonNode inactiveWithCapacity =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?q=" + marker + "&active=false&minFree=1",
                null),
            200);
    assertThat(inactiveWithCapacity.get("totalElements").asLong()).isZero();
  }

  @Test
  void largeRequestedPagesAreClampedToTheLastAvailablePage() throws Exception {
    String marker = "page-" + unique();
    createDoctor(marker, "Page", "Only", true);
    JsonNode result =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/doctors?q=" + marker + "&page=2000000000&size=1",
                null),
            200);
    assertThat(result.get("page").asInt()).isZero();
    assertThat(result.get("items").size()).isEqualTo(1);
  }

  @Test
  void catalogueSearchAndCountsStayInsideTheSelectedDepartment() throws Exception {
    long homeDepartment =
        result(request("admin", "GET", "/api/v1/workspaces", null), 200)
            .get("activeDepartmentId")
            .asLong();
    JsonNode newDepartment =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Catalogue Isolation " + unique(), "departmentName", "Imaging")),
            201);
    long otherDepartment = newDepartment.get("departmentId").asLong();
    String marker = "dept-" + unique();
    for (String catalogue : List.of("doctors", "rooms", "procedures")) {
      JsonNode home = createScopedCatalogue(catalogue, marker, "home", homeDepartment);
      JsonNode away = createScopedCatalogue(catalogue, marker, "away", otherDepartment);
      assertOnlyCatalogueItem(homeDepartment, marker, catalogue, home);
      assertOnlyCatalogueItem(otherDepartment, marker, catalogue, away);
    }
  }

  private JsonNode createScopedCatalogue(
      String catalogue, String marker, String suffix, long department) throws Exception {
    String identifier = marker + "-" + catalogue + "-" + suffix;
    Object input =
        switch (catalogue) {
          case "doctors" ->
              Map.of(
                  "doctorIdentifier", identifier,
                  "firstName", suffix,
                  "lastName", marker,
                  "specialty", marker,
                  "active", true);
          case "rooms" -> Map.of("roomNumber", identifier, "bedCount", 2, "active", true);
          case "procedures" ->
              Map.of(
                  "procedureCode", identifier,
                  "procedureName", suffix + " " + marker,
                  "currentCost", 10,
                  "active", true);
          default -> throw new IllegalArgumentException("Unsupported catalogue");
        };
    return result(
        scopedRequest("admin", "POST", "/api/v1/" + catalogue, input, department), 201);
  }

  private JsonNode createDoctor(String marker, String firstName, String lastName, boolean active)
      throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/doctors",
            Map.of(
                "doctorIdentifier", marker + "-" + firstName,
                "firstName", firstName,
                "lastName", lastName,
                "specialty", marker,
                "active", active)),
        201);
  }

  private JsonNode createProcedure(String marker, String name, boolean active) throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/procedures",
            Map.of(
                "procedureCode", marker + "-" + name,
                "procedureName", name,
                "currentCost", new BigDecimal("12.50"),
                "active", active)),
        201);
  }

  private JsonNode createRoom(String roomNumber, int beds, boolean active) throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/rooms",
            Map.of("roomNumber", roomNumber, "bedCount", beds, "active", active)),
        201);
  }

  private void assertOnlyCatalogueItem(
      long department, String marker, String catalogue, JsonNode expected) throws Exception {
    JsonNode page =
        result(
            scopedRequest(
                "admin", "GET", "/api/v1/" + catalogue + "?q=" + marker, null, department),
            200);
    assertThat(page.get("totalElements").asLong()).isEqualTo(1);
    assertThat(page.get("items").get(0).get("id").asLong())
        .isEqualTo(expected.get("id").asLong());
  }

  private ResultActions scopedRequest(
      String who, String method, String path, Object body, long department) throws Exception {
    MockHttpServletRequestBuilder request =
        "POST".equals(method) ? post(path) : get(path);
    request.with(user(who)).with(csrf()).header("X-Department-Id", department);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  private void occupyRoom(JsonNode room) throws Exception {
    JsonNode patient = createPatient();
    long departmentId =
        jdbc.queryForObject(
            "select department_id from rooms where id=?", Long.class, room.get("id").asLong());
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    long admissionId =
        jdbc.queryForObject(
            "insert into admissions (admission_number, patient_id, attending_doctor_id, "
                + "admission_date_time, status, created_by, department_id) "
                + "values (?, ?, 1, now(), 'ACTIVE', ?, ?) returning id",
            Long.class,
            "CATALOGUE-" + unique(),
            patient.get("id").asLong(),
            adminId,
            departmentId);
    jdbc.update(
        "insert into room_assignments (admission_id, room_id, assigned_at, created_by, "
            + "department_id) values (?, ?, now(), ?, ?)",
        admissionId,
        room.get("id").asLong(),
        adminId,
        departmentId);
  }

  private static JsonNode findItem(JsonNode page, long id) {
    for (JsonNode item : page.get("items")) {
      if (item.get("id").asLong() == id) return item;
    }
    throw new AssertionError("Room not found in page items");
  }
}
