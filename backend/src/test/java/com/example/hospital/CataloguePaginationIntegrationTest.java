package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CataloguePaginationIntegrationTest extends HospitalSupport {
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
    createRoom(marker + "-available", 2, true);
    createRoom(marker + "-inactive", 2, false);

    JsonNode available =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?q=" + marker + "&active=true&minFree=2&size=1",
                null),
            200);
    assertThat(available.get("totalElements").asLong()).isEqualTo(1);
    assertThat(available.get("items").get(0).get("occupiedBeds").asLong()).isZero();
    assertThat(available.get("items").get(0).get("availableBeds").asInt()).isEqualTo(2);

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
}
