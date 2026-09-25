package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OwnershipTransferTest extends HospitalSupport {
  private record Setup(long hospitalId, String hospitalName, String member, long memberId) {}

  private Setup hospitalWithMember() throws Exception {
    String member = "heir-" + unique();
    var body = new HashMap<String, Object>();
    body.put("username", member);
    body.put("password", "TransferPassword123!");
    body.put("role", "MEDICAL_STAFF");
    body.put("enabled", true);
    long memberId = result(request("admin", "POST", "/api/v1/users", body), 201).get("id").asLong();
    String name = "Transfer " + unique();
    var hospital = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", name, "departmentName", "Ward")), 201);
    result(request(member, "POST", "/api/v1/workspaces/join",
        Map.of("code", hospital.path("hospitalCode").asText())), 200);
    return new Setup(hospital.path("hospitalId").asLong(), name, member, memberId);
  }

  private boolean owner(long hospitalId, String username) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "select m.owner from hospital_memberships m join app_users u on u.id = m.user_id where m.hospital_id = ? and u.username = ?",
        Boolean.class, hospitalId, username));
  }

  @Test
  void handoverNeedsTypedConfirmationAndTakesEffectOnlyWhenAccepted() throws Exception {
    var s = hospitalWithMember();
    String transfers = "/api/v1/workspaces/hospitals/" + s.hospitalId() + "/ownership-transfers";

    request("admin", "POST", transfers, Map.of("userId", s.memberId(), "stepDown", true, "confirmation", "wrong"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));

    var transfer = result(request("admin", "POST", transfers,
        Map.of("userId", s.memberId(), "stepDown", true, "confirmation", s.hospitalName(), "reason", "Leaving the trust")), 202);
    assertThat(transfer.get("status").asText()).isEqualTo("PENDING");
    assertThat(owner(s.hospitalId(), s.member())).isFalse();
    assertThat(owner(s.hospitalId(), "admin")).isTrue();

    var incoming = result(request(s.member(), "GET", "/api/v1/workspaces/ownership-transfers", null), 200).get("incoming");
    assertThat(incoming.toString()).contains("\"id\":" + transfer.get("id").asLong());
    request("admin", "POST", "/api/v1/workspaces/ownership-transfers/" + transfer.get("id").asLong() + "/accept", null)
        .andExpect(status().isNotFound());

    result(request(s.member(), "POST", "/api/v1/workspaces/ownership-transfers/" + transfer.get("id").asLong() + "/accept", null), 200);
    assertThat(owner(s.hospitalId(), s.member())).isTrue();
    assertThat(owner(s.hospitalId(), "admin")).isFalse();
    assertThat(jdbc.queryForObject(
            "select count(*) from audit_events where entity_id = ? and event_type in ('OWNERSHIP_TRANSFER_REQUESTED', 'HOSPITAL_OWNER_GRANTED', 'HOSPITAL_OWNER_STEPPED_DOWN')",
            Long.class, s.hospitalId()))
        .isEqualTo(3);
    request(s.member(), "POST", "/api/v1/workspaces/ownership-transfers/" + transfer.get("id").asLong() + "/accept", null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
  }

  @Test
  void coOwnerInvitationsCanBeDeclinedOrCancelledAndAreNotDuplicated() throws Exception {
    var s = hospitalWithMember();
    String owners = "/api/v1/workspaces/hospitals/" + s.hospitalId() + "/owners";
    var invite = result(request("admin", "POST", owners, Map.of("userId", s.memberId())), 202);
    request("admin", "POST", owners, Map.of("userId", s.memberId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TRANSFER_PENDING"));
    result(request(s.member(), "POST", "/api/v1/workspaces/ownership-transfers/" + invite.get("id").asLong() + "/decline", null), 200);
    assertThat(owner(s.hospitalId(), s.member())).isFalse();

    var second = result(request("admin", "POST", owners, Map.of("userId", s.memberId())), 202);
    var cancelled = result(request("admin", "POST", "/api/v1/workspaces/ownership-transfers/" + second.get("id").asLong() + "/cancel", null), 200);
    assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
    request(s.member(), "POST", "/api/v1/workspaces/ownership-transfers/" + second.get("id").asLong() + "/accept", null)
        .andExpect(status().isNotFound());
    assertThat(owner(s.hospitalId(), s.member())).isFalse();
  }

  @Test
  void onlyOwnersRequestAndOnlyMembersReceive() throws Exception {
    var s = hospitalWithMember();
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    request(s.member(), "POST", "/api/v1/workspaces/hospitals/" + s.hospitalId() + "/owners", Map.of("userId", adminId))
        .andExpect(status().isForbidden());
    request("admin", "POST", "/api/v1/workspaces/hospitals/" + s.hospitalId() + "/owners", Map.of("userId", adminId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TRANSFER"));
    long outsider = users.findByUsername("doctor").orElseThrow().getId();
    request("admin", "POST", "/api/v1/workspaces/hospitals/" + s.hospitalId() + "/owners", Map.of("userId", outsider))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
  }
}
