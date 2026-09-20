package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(r1.get("id").asLong())).isOne();
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
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(r1.get("id").asLong())).isZero();
    assertThat(assignments.findByAdmissionIdOrderByAssignedAt(id)).hasSize(2);
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + id + "/discharge",
            Map.of("version", moved.get("version").asLong()))
        .andExpect(status().isOk());
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(r2.get("id").asLong())).isZero();
    assertThat(admissions.findById(id).orElseThrow().getStatus()).isEqualTo("DISCHARGED");
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
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(r.get("id").asLong())).isOne();
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
}
