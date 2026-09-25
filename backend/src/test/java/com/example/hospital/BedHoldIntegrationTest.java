package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

class BedHoldIntegrationTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void timedHoldsReduceAvailabilityAndBlockPlacementUntilReleased() throws Exception {
    var room = room(2);
    var now = Instant.now();
    var hold =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
                Map.of(
                    "bedCount",
                    1,
                    "reason",
                    "Electrical inspection",
                    "startsAt",
                    now.plusSeconds(3600).toString(),
                    "endsAt",
                    now.plusSeconds(7200).toString())),
            201);

    assertThat(hold.get("reason").asText()).isEqualTo("Electrical inspection");
    var matching = roomView(room.get("id").asLong(), 0).path("items").get(0);
    assertThat(matching.get("heldBeds").asInt()).isEqualTo(1);
    assertThat(matching.get("activeHeldBeds").asInt()).isZero();
    assertThat(matching.get("availableBeds").asInt()).isEqualTo(1);
    var dashboard = result(request("admin", "GET", "/api/v1/reports/dashboard", null), 200);
    assertThat(dashboard.get("heldBeds").asLong()).isGreaterThanOrEqualTo(1);
    var minFree = roomView(room.get("id").asLong(), 2);
    assertThat(minFree.path("totalElements").asLong()).isZero();

    admit(createPatient(), room);
    var simulation = result(request("admin", "GET", "/api/v1/planner/simulate?arrivals=100", null), 200);
    assertThat(java.util.stream.StreamSupport.stream(simulation.get("placements").spliterator(), false)
            .filter(placement -> placement.get("roomId").asLong() == room.get("id").asLong())
            .count())
        .isZero();
    request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId", createPatient().get("id").asLong(),
                "doctorId", 1,
                "roomId", room.get("id").asLong()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));

    request(
            "admin",
            "DELETE",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds/" + hold.get("id").asLong(),
            null)
        .andExpect(status().isNoContent());
    var afterRelease = roomView(room.get("id").asLong(), 0);
    var available = afterRelease.path("items").get(0).get("availableBeds").asInt();
    assertThat(available).isEqualTo(1);
    admit(createPatient(), room);
  }

  @Test
  void holdWindowsMustFitUnoccupiedCapacityAndRoomEditsRespectReservations() throws Exception {
    var room = room(3);
    var now = Instant.now();
    var hold =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
                Map.of(
                    "bedCount",
                    2,
                    "reason",
                    "Floor repair",
                    "startsAt",
                    now.plusSeconds(600).toString(),
                    "endsAt",
                    now.plusSeconds(1800).toString())),
            201);
    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + room.get("id").asLong(),
            Map.of(
                "roomNumber", room.get("roomNumber").asText(),
                "bedCount", 1,
                "active", true,
                "version", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"));

    request(
            "admin",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount",
                2,
                "reason",
                "Overlapping work",
                "startsAt",
                now.plusSeconds(1200).toString(),
                "endsAt",
                now.plusSeconds(2400).toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));

    request(
            "doctor",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount",
                1,
                "reason",
                "Unauthorized",
                "startsAt",
                now.plusSeconds(3600).toString(),
                "endsAt",
                now.plusSeconds(7200).toString()))
        .andExpect(status().isForbidden());

    request(
            "admin",
            "DELETE",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds/" + hold.get("id").asLong(),
            null)
        .andExpect(status().isNoContent());
  }

  @Test
  void adjacentMaintenanceWindowsDoNotDoubleCountTheSameCapacity() throws Exception {
    var room = room(2);
    var start = Instant.now().plusSeconds(600);
    var middle = start.plusSeconds(1200);
    var end = middle.plusSeconds(1200);
    request(
            "admin",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount", 2,
                "reason", "First service window",
                "startsAt", start.toString(),
                "endsAt", middle.toString()))
        .andExpect(status().isCreated());
    request(
            "admin",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount", 2,
                "reason", "Second service window",
                "startsAt", middle.toString(),
                "endsAt", end.toString()))
        .andExpect(status().isCreated());
    var held = roomView(room.get("id").asLong(), 0).path("items").get(0).get("heldBeds").asInt();
    assertThat(held).isEqualTo(2);
  }

  @Test
  void maintenanceWindowsThatCollapseAtDatabasePrecisionAreRejected() throws Exception {
    var room = room(1);
    var start = Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.MICROS);
    request(
            "admin",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount", 1,
                "reason", "Sub-microsecond window",
                "startsAt", start.toString(),
                "endsAt", start.plusNanos(999).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_HOLD_WINDOW"));
  }

  @Test
  void expiredHoldsStopReservingCapacityWithoutABackgroundJob() throws Exception {
    var room = room(1);
    var now = Instant.now();
    var hold =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
                Map.of(
                    "bedCount", 1,
                    "reason", "Short inspection",
                    "startsAt", now.plusSeconds(60).toString(),
                    "endsAt", now.plusSeconds(600).toString())),
            201);
    jdbc.update(
        "update bed_holds set starts_at=?, ends_at=? where id=?",
        java.sql.Timestamp.from(now.minusSeconds(60)),
        java.sql.Timestamp.from(now.minusSeconds(30)),
        hold.get("id").asLong());

    var matching = roomView(room.get("id").asLong(), 0).path("items").get(0);
    assertThat(matching.get("heldBeds").asInt()).isZero();
    assertThat(matching.get("activeHeldBeds").asInt()).isZero();
    assertThat(matching.get("availableBeds").asInt()).isEqualTo(1);
  }

  @Test
  void inactiveRoomsRejectNewMaintenanceHolds() throws Exception {
    var room = room(2);
    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + room.get("id").asLong(),
            Map.of(
                "roomNumber", room.get("roomNumber").asText(),
                "bedCount", 2,
                "active", false,
                "version", 0))
        .andExpect(status().isOk());
    var now = Instant.now();
    request(
            "admin",
            "POST",
            "/api/v1/rooms/" + room.get("id").asLong() + "/holds",
            Map.of(
                "bedCount", 1,
                "reason", "Unneeded while inactive",
                "startsAt", now.plusSeconds(60).toString(),
                "endsAt", now.plusSeconds(600).toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
  }

  private com.fasterxml.jackson.databind.JsonNode roomView(long roomId, int minFree) throws Exception {
    return result(request("admin", "GET", "/api/v1/rooms?roomId=" + roomId + "&minFree=" + minFree, null), 200);
  }
}
