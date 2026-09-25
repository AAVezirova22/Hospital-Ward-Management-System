package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionChangeAuditTest extends HospitalSupport {
  @Test
  void departmentRoleAuditCapturesTargetScopeBeforeAfterAndReasonWithoutCredentials() throws Exception {
    String username = "permission" + unique();
    var user = result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "CredentialSentinel123!", "role", "MEDICAL_STAFF", "enabled", true)), 201);
    long userId = user.path("id").asLong();
    long departmentId = result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .path("activeDepartmentId").asLong();

    result(request("admin", "POST", "/api/v1/workspaces/departments/" + departmentId + "/roles",
        Map.of("userId", userId, "role", "ADMIN", "reason", "coverage reassignment")), 200);

    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='DEPARTMENT_ROLE_GRANTED' and entity_id=? and metadata like ? order by id desc limit 1",
        String.class, departmentId, "%targetUserId=" + userId + "%");
    assertThat(metadata).contains("targetUserId=" + userId, "workspaceType=DEPARTMENT", "workspaceId=" + departmentId,
        "oldRole=MEDICAL_STAFF", "newRole=ADMIN", "reason=coverage reassignment")
        .doesNotContain("CredentialSentinel123!", "password", "token");

    result(request("admin", "PUT", "/api/v1/users/" + userId, Map.of(
        "username", username, "role", "MEDICAL_STAFF", "enabled", true, "version", user.path("version").asLong())), 200);
    String accountMetadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='USER_SAVED' and entity_id=? order by id desc limit 1",
        String.class, userId);
    assertThat(accountMetadata).contains("oldAccountRole=MEDICAL_STAFF", "newAccountRole=MEDICAL_STAFF",
        "oldDepartmentRole=ADMIN", "newDepartmentRole=MEDICAL_STAFF");
  }

  @Test
  void ownerGrantAuditCapturesMembershipToOwnerTransitionAndReason() throws Exception {
    String username = "ownerchange" + unique();
    var user = result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "CredentialSentinel456!", "role", "MEDICAL_STAFF", "enabled", true)), 201);
    long userId = user.path("id").asLong();
    var hospital = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Audit " + unique(), "departmentName", "Ward")), 201);
    long hospitalId = hospital.path("hospitalId").asLong();
    result(request(username, "POST", "/api/v1/workspaces/join",
        Map.of("code", hospital.path("departmentCode").asText())), 200);

    request("admin", "POST", "/api/v1/workspaces/hospitals/" + hospitalId + "/owners",
        Map.of("userId", userId, "reason", "token=superSecretValue123"))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type='HOSPITAL_OWNER_GRANTED' and entity_id=?",
        Long.class, hospitalId)).isZero();

    var invitation = result(request("admin", "POST", "/api/v1/workspaces/hospitals/" + hospitalId + "/owners",
        Map.of("userId", userId, "reason", "on-call owner")), 202);
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type='HOSPITAL_OWNER_GRANTED' and entity_id=?",
        Long.class, hospitalId)).as("ownership changes only on acceptance").isZero();
    result(request(username, "POST", "/api/v1/workspaces/ownership-transfers/" + invitation.path("id").asLong() + "/accept", null), 200);

    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='HOSPITAL_OWNER_GRANTED' and entity_id=? order by id desc limit 1",
        String.class, hospitalId);
    assertThat(metadata).contains("targetUserId=" + userId, "workspaceType=HOSPITAL", "workspaceId=" + hospitalId,
        "oldRole=MEMBER", "newRole=OWNER", "oldOwner=false", "newOwner=true", "reason=on-call owner")
        .doesNotContain("CredentialSentinel456!", "password", "token");
  }

  @Test
  void crossDepartmentOwnerChangeIsStoredInTheTargetDepartmentScope() throws Exception {
    long activeDepartment = result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .path("activeDepartmentId").asLong();
    var hospital = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Cross scope " + unique(), "departmentName", "First")), 201);
    long hospitalId = hospital.path("hospitalId").asLong();
    var targetDepartment = result(request("admin", "POST",
        "/api/v1/workspaces/hospitals/" + hospitalId + "/departments", Map.of("name", "Target")), 201);
    long targetDepartmentId = targetDepartment.path("departmentId").asLong();
    String username = "crossscope" + unique();
    var user = result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "CrossScopePassword123!", "role", "MEDICAL_STAFF", "enabled", true)), 201);
    long userId = user.path("id").asLong();
    result(request(username, "POST", "/api/v1/workspaces/join",
        Map.of("code", targetDepartment.path("joinCode").asText())), 200);

    result(request("admin", "POST", "/api/v1/workspaces/departments/" + targetDepartmentId + "/roles",
        Map.of("userId", userId, "role", "DOCTOR")), 200);

    Long auditDepartment = jdbc.queryForObject(
        "select department_id from audit_events where event_type='DEPARTMENT_ROLE_GRANTED' and entity_id=? and metadata like ? order by id desc limit 1",
        Long.class, targetDepartmentId, "%targetUserId=" + userId + "%");
    assertThat(auditDepartment).isEqualTo(targetDepartmentId).isNotEqualTo(activeDepartment);
    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='DEPARTMENT_ROLE_GRANTED' and entity_id=? and metadata like ? order by id desc limit 1",
        String.class, targetDepartmentId, "%targetUserId=" + userId + "%");
    assertThat(metadata).contains("workspaceId=" + targetDepartmentId, "hospitalId=" + hospitalId,
        "oldRole=MEDICAL_STAFF", "newRole=DOCTOR").doesNotContain("oldOwner", "newOwner");
    assertThat(result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .path("activeDepartmentId").asLong()).isEqualTo(activeDepartment);
  }

  @Test
  void departmentRoleAuditCapturesDoctorRelink() throws Exception {
    String username = "doctorlink" + unique();
    var user = result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "DoctorLinkPassword123!", "role", "MEDICAL_STAFF", "enabled", true)), 201);
    long userId = user.path("id").asLong();
    long departmentId = result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .path("activeDepartmentId").asLong();
    long oldDoctorId = result(request("admin", "POST", "/api/v1/workspaces/departments/" + departmentId + "/roles",
        Map.of("userId", userId, "role", "DOCTOR")), 200).path("doctorId").asLong();
    long newDoctorId = jdbc.queryForObject(
        "insert into doctors(doctor_identifier,first_name,last_name,specialty,active,department_id,version,created_at,updated_at) values (?,?,?,'General medicine',true,?,0,now(),now()) returning id",
        Long.class, "RELINK-" + unique(), "Audit", "Doctor", departmentId);

    result(request("admin", "POST", "/api/v1/workspaces/departments/" + departmentId + "/roles",
        Map.of("userId", userId, "role", "DOCTOR", "doctorId", newDoctorId)), 200);

    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type='DEPARTMENT_ROLE_GRANTED' and entity_id=? and metadata like ? order by id desc limit 1",
        String.class, departmentId, "%targetUserId=" + userId + "%");
    assertThat(metadata).contains("oldDoctorId=" + oldDoctorId, "newDoctorId=" + newDoctorId)
        .doesNotContain("oldOwner", "newOwner");
  }

  @Test
  void hospitalRevocationAuditsEachDepartmentMembershipRemoved() throws Exception {
    var hospital = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Multi dept revoke " + unique(), "departmentName", "First")), 201);
    long hospitalId = hospital.path("hospitalId").asLong();
    long firstDepartmentId = hospital.path("departmentId").asLong();
    var secondDepartment = result(request("admin", "POST",
        "/api/v1/workspaces/hospitals/" + hospitalId + "/departments", Map.of("name", "Second")), 201);
    long secondDepartmentId = secondDepartment.path("departmentId").asLong();
    String username = "multirevoke" + unique();
    var user = result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "MultiRevokePassword123!", "role", "MEDICAL_STAFF", "enabled", true)), 201);
    long userId = user.path("id").asLong();
    result(request(username, "POST", "/api/v1/workspaces/join",
        Map.of("code", hospital.path("departmentCode").asText())), 200);
    result(request(username, "POST", "/api/v1/workspaces/join",
        Map.of("code", secondDepartment.path("joinCode").asText())), 200);
    assertThat(jdbc.queryForObject(
        "select metadata from audit_events where event_type='WORKSPACE_JOINED' and department_id=? and metadata like ? order by id desc limit 1",
        String.class, firstDepartmentId, "%targetUserId=" + userId + "%"))
        .contains("hospitalMembershipCreated=true");
    assertThat(jdbc.queryForObject(
        "select metadata from audit_events where event_type='WORKSPACE_JOINED' and department_id=? and metadata like ? order by id desc limit 1",
        String.class, secondDepartmentId, "%targetUserId=" + userId + "%"))
        .contains("hospitalMembershipCreated=false");

    request("admin", "DELETE", "/api/v1/workspaces/hospitals/" + hospitalId + "/members/" + userId, null)
        .andExpect(status().isOk());

    var affectedDepartments = jdbc.queryForList(
        "select department_id from audit_events where event_type='DEPARTMENT_MEMBER_REVOKED' and metadata like ? order by department_id",
        Long.class, "%targetUserId=" + userId + "%");
    assertThat(affectedDepartments).containsExactlyInAnyOrder(firstDepartmentId, secondDepartmentId);
    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type='DEPARTMENT_MEMBER_REVOKED' and department_id in (?,?) and metadata like ? and metadata like '%newRole=NONE%'",
        Long.class, firstDepartmentId, secondDepartmentId, "%targetUserId=" + userId + "%")).isEqualTo(2L);
    var oldRoles = jdbc.queryForList(
        "select metadata from audit_events where event_type='DEPARTMENT_MEMBER_REVOKED' and department_id in (?,?) and metadata like ?",
        String.class, firstDepartmentId, secondDepartmentId, "%targetUserId=" + userId + "%");
    assertThat(oldRoles).allSatisfy(metadata -> assertThat(metadata)
        .contains("oldRole=MEDICAL_STAFF", "newRole=NONE", "hospitalId=" + hospitalId)
        .doesNotContain("oldOwner", "newOwner"));
  }
}
