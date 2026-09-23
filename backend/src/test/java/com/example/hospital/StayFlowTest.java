package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StayFlowTest extends HospitalSupport {
  @Test
  void admissionTransferAndDischargeReleaseCapacity() throws Exception {
    var p = createPatient();
    var r1 = room(1);
    var r2 = room(1);
    var a = admit(p, r1);
    long id = a.get("id").asLong();
    assertThat(activeAssignments(r1.get("id").asLong())).isOne();
    var moved =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions/" + id + "/transfer",
                Map.of(
                    "roomId",
                    r2.get("id").asLong(),
                    "reason",
                    "Step-down placement",
                    "version",
                    a.get("version").asLong())),
            200);
    assertThat(activeAssignments(r1.get("id").asLong())).isZero();
    assertThat(assignmentCount(id)).isEqualTo(2);
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + id + "/discharge",
            Map.of("version", moved.get("version").asLong()))
        .andExpect(status().isOk());
    assertThat(activeAssignments(r2.get("id").asLong())).isZero();
    assertThat(jdbc.queryForObject("select status from admissions where id = ?", String.class, id))
        .isEqualTo("DISCHARGED");
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + id + "/discharge",
            Map.of("version", moved.get("version").asLong()))
        .andExpect(status().isConflict());
  }

  @Test
  void fullRoomsAndDuplicateActiveAdmissionsAreRejected() throws Exception {
    var p = createPatient();
    var r = room(1);
    admit(p, r);
    var other = createPatient();
    request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId",
                other.get("id").asLong(),
                "doctorId",
                1,
                "roomId",
                r.get("id").asLong()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));
    var empty = room(2);
    request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId",
                p.get("id").asLong(),
                "doctorId",
                1,
                "roomId",
                empty.get("id").asLong()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_ADMITTED"));
  }

  @Test
  void staleTransferAndSameRoomAreRejectedWithoutLosingBed() throws Exception {
    var r = room(2);
    var a = admit(createPatient(), r);
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + a.get("id").asLong() + "/transfer",
            Map.of("roomId", r.get("id").asLong(), "reason", "test", "version", 999))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_STATE"));
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + a.get("id").asLong() + "/transfer",
            Map.of("roomId", r.get("id").asLong(), "reason", "test", "version", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SAME_ROOM"));
    assertThat(activeAssignments(r.get("id").asLong())).isOne();
  }

  @Test
  void capacityCannotBeReducedBelowOccupancy() throws Exception {
    var r = room(2);
    admit(createPatient(), r);
    admit(createPatient(), r);
    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + r.get("id").asLong(),
            Map.of(
                "roomNumber",
                r.get("roomNumber").asText(),
                "bedCount",
                1,
                "active",
                true,
                "version",
                0))
        .andExpect(status().isConflict());
    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + r.get("id").asLong(),
            Map.of(
                "roomNumber",
                r.get("roomNumber").asText(),
                "bedCount",
                2,
                "active",
                false,
                "version",
                0))
        .andExpect(status().isConflict());
  }

  @Test
  void doctorReassignmentUpdatesAccessAndInactiveRoomsRejectPlacement() throws Exception {
    var p = createPatient();
    var r = room(1);
    var a = admit(p, r);
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + a.get("id").asLong() + "/doctor",
            Map.of("doctorId", 2, "version", 0))
        .andExpect(status().isOk());
    request("doctor", "GET", "/api/v1/admissions/" + a.get("id").asLong(), null)
        .andExpect(status().isForbidden());
    var inactive = room(1);
    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + inactive.get("id").asLong(),
            Map.of(
                "roomNumber",
                inactive.get("roomNumber").asText(),
                "bedCount",
                1,
                "active",
                false,
                "version",
                0))
        .andExpect(status().isOk());
    request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId",
                createPatient().get("id").asLong(),
                "doctorId",
                1,
                "roomId",
                inactive.get("id").asLong()))
        .andExpect(status().isConflict());
  }

  @Test
  void roomCapabilitiesFilterPlacementAndRemainEnforcedForTransfers() throws Exception {
    String capableNumber = "C-" + unique();
    var capable =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms",
                Map.of(
                    "roomNumber", capableNumber,
                    "bedCount", 1,
                    "active", true,
                    "capabilities", List.of(" Oxygen ", "isolation"))),
            201);
    var incompatible = room(1);
    var search =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/rooms?requiredCapabilities=oxygen&requiredCapabilities=isolation",
                null),
            200);
    assertThat(search.findValuesAsText("roomNumber")).contains(capableNumber);
    assertThat(search.findValuesAsText("roomNumber"))
        .doesNotContain(incompatible.get("roomNumber").asText());

    var patient = createPatient();
    var admission =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions",
                Map.of(
                    "patientId", patient.get("id").asLong(),
                    "doctorId", 1,
                    "roomId", capable.get("id").asLong(),
                    "requiredRoomCapabilities", List.of(" OXYGEN "))),
            201);
    assertThat(admission.path("requiredRoomCapabilities").size()).isEqualTo(1);
    assertThat(admission.path("requiredRoomCapabilities").get(0).asText()).isEqualTo("oxygen");

    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + admission.get("id").asLong() + "/transfer",
            Map.of(
                "roomId", incompatible.get("id").asLong(),
                "reason", "Capability guard",
                "version", admission.get("version").asLong()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_MISMATCH"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("oxygen")));

    request(
            "admin",
            "PUT",
            "/api/v1/rooms/" + capable.get("id").asLong(),
            Map.of(
                "roomNumber", capableNumber,
                "bedCount", 1,
                "active", true,
                "capabilities", List.of("isolation"),
                "version", capable.get("version").asLong()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPABILITY_IN_USE"));
  }

  @Test
  void legacyRoomUpdatesPreserveCapabilitiesWhenFieldIsOmitted() throws Exception {
    var created =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/rooms",
                Map.of(
                    "roomNumber", "L-" + unique(),
                    "bedCount", 2,
                    "active", true,
                    "capabilities", List.of("oxygen"))),
            201);

    var updated =
        result(
            request(
                "admin",
                "PUT",
                "/api/v1/rooms/" + created.get("id").asLong(),
                Map.of(
                    "roomNumber", created.get("roomNumber").asText(),
                    "bedCount", 3,
                    "active", true,
                    "version", created.get("version").asLong())),
            200);

    assertThat(updated.path("capabilities").get(0).asText()).isEqualTo("oxygen");
  }
}
