package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.service.MembershipExpiryNotices;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MembershipExpiryTest extends HospitalSupport {
  @Autowired MembershipExpiryNotices notices;
  private String username;
  private long userId;

  @BeforeEach
  void temporaryMember() throws Exception {
    username = "temp-" + unique();
    var body = new HashMap<String, Object>();
    body.put("username", username);
    body.put("password", "TemporaryPassword123!");
    body.put("role", "MEDICAL_STAFF");
    body.put("enabled", true);
    userId = result(request("admin", "POST", "/api/v1/users", body), 201).get("id").asLong();
  }

  private String expiry(long user) {
    return "/api/v1/workspaces/departments/1/members/" + user + "/expiry";
  }

  private Map<String, Object> at(Instant instant) {
    var body = new HashMap<String, Object>();
    body.put("expiresAt", instant == null ? null : instant.toString());
    return body;
  }

  @Test
  void accessEndsAtTheExpiryAndStaleSessionsFallBack() throws Exception {
    request("admin", "PUT", expiry(userId), at(Instant.now().plus(10, ChronoUnit.DAYS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId));
    mvc.perform(get("/api/v1/patients").header("X-Department-Id", "1").with(user(username)))
        .andExpect(status().isOk());

    jdbc.update(
        "update department_memberships set expires_at = now() - interval '1 minute' where department_id = 1 and user_id = ?",
        userId);

    mvc.perform(get("/api/v1/patients").header("X-Department-Id", "1").with(user(username)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERSHIP_EXPIRED"));
    var workspaces = result(request(username, "GET", "/api/v1/workspaces", null), 200);
    assertThat(workspaces.toString()).doesNotContain("\"id\":1,\"name\":\"General medicine\"");

    request("admin", "PUT", expiry(userId), at(null)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/patients").header("X-Department-Id", "1").with(user(username)))
        .andExpect(status().isOk());
  }

  @Test
  void onlyAdministratorsSetFutureExpiriesOnOtherMembers() throws Exception {
    request("admin", "PUT", expiry(userId), at(Instant.now().minusSeconds(60)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EXPIRY"));
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    request("admin", "PUT", expiry(adminId), at(Instant.now().plus(1, ChronoUnit.DAYS)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SELF_EXPIRY"));
    request("staff", "PUT", expiry(userId), at(Instant.now().plus(1, ChronoUnit.DAYS)))
        .andExpect(status().isForbidden());
    request("admin", "PUT", expiry(999999999L), at(Instant.now().plus(1, ChronoUnit.DAYS)))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where event_type = 'MEMBERSHIP_EXPIRY_SET' and metadata ~ ?",
                Long.class,
                "userId=" + userId + "[,}]"))
        .isZero();
  }

  @Test
  void membersGetOneAdvanceNoticeAndTheTeamListShowsTheEnd() throws Exception {
    Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
    request("admin", "PUT", expiry(userId), at(ends)).andExpect(status().isOk());

    notices.notifyExpiring();
    assertThat(notices.notifyExpiring()).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notifications where recipient_user_id = ? and type = 'MEMBERSHIP_EXPIRING'",
                Long.class,
                userId))
        .isOne();

    var team = result(request("admin", "GET", "/api/v1/users", null), 200);
    boolean shown = false;
    for (var account : team)
      if (account.get("id").asLong() == userId)
        shown = !account.get("membershipExpiresAt").isNull();
    assertThat(shown).isTrue();

    request("admin", "PUT", expiry(userId), at(ends.plus(1, ChronoUnit.HOURS))).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select expiry_notified_at is null from department_memberships where department_id = 1 and user_id = ?",
                Boolean.class,
                userId))
        .isTrue();
  }
}
