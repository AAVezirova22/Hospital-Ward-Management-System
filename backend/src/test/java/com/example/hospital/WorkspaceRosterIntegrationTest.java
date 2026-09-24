package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceRosterIntegrationTest extends HospitalSupport {
  @Test
  void ownersAndDepartmentAdminsCanPageScopedRosterDetails() throws Exception {
    var hospital = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Roster " + unique(), "departmentName", "Roster ward")), 201);
    long hospitalId = hospital.path("hospitalId").asLong();
    long departmentId = hospital.path("departmentId").asLong();

    String staffName = "rosterstaff" + unique();
    String doctorName = "rosterdoctor" + unique();
    String disabledName = "rosterdisabled" + unique();
    createMember(staffName, true);
    var doctor = createMember(doctorName, true);
    var disabled = createMember(disabledName, false);
    joinDepartment(staffName, hospital.path("departmentCode").asText());
    joinDepartment(doctorName, hospital.path("departmentCode").asText());
    var linked = result(request("admin", "POST", "/api/v1/workspaces/departments/" + departmentId + "/roles",
        Map.of("userId", doctor.path("id").asLong(), "role", "DOCTOR")), 200);
    jdbc.update("insert into hospital_memberships(hospital_id,user_id,joined_at) values (?,?,null)",
        hospitalId, disabled.path("id").asLong());
    jdbc.update("insert into department_memberships(department_id,user_id,role,joined_at) values (?,?,'MEDICAL_STAFF',null)",
        departmentId, disabled.path("id").asLong());

    var departmentPage = result(request("admin", "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members?page=0&size=1", null), 200);
    assertThat(departmentPage.path("page").asInt()).isZero();
    assertThat(departmentPage.path("size").asInt()).isEqualTo(1);
    assertThat(departmentPage.path("totalElements").asLong()).isEqualTo(4L);
    assertThat(departmentPage.path("hasNext").asBoolean()).isTrue();
    var clampedDepartmentPage = result(request("admin", "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members?page=-3&size=500", null), 200);
    assertThat(clampedDepartmentPage.path("page").asInt()).isZero();
    assertThat(clampedDepartmentPage.path("size").asInt()).isEqualTo(100);
    var clampedLastPage = result(request("admin", "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members?page=9999&size=1", null), 200);
    assertThat(clampedLastPage.path("page").asInt()).isEqualTo(3);

    var allDepartmentMembers = result(request("admin", "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members?size=100", null), 200).path("items");
    var disabledMember = findByUsername(allDepartmentMembers, disabledName);
    assertThat(disabledMember.path("enabled").asBoolean()).isFalse();
    assertThat(disabledMember.path("role").asText()).isEqualTo("MEDICAL_STAFF");
    assertThat(disabledMember.path("joinedAt").isNull()).isTrue();
    assertThat(allDepartmentMembers.toString()).doesNotContain("passwordHash", "sessionStamp", "patientId", "email");

    var doctorMember = findByUsername(allDepartmentMembers, doctorName);
    assertThat(doctorMember.path("doctorId").asLong()).isEqualTo(linked.path("doctorId").asLong());
    assertThat(doctorMember.path("doctorIdentifier").asText()).isNotBlank();
    assertThat(doctorMember.path("doctorName").asText()).isNotBlank();
    assertThat(doctorMember.path("joinedAt").isNull()).isFalse();

    String staffJoinedAt = findByUsername(allDepartmentMembers, staffName).path("joinedAt").asText();
    result(request("admin", "POST", "/api/v1/workspaces/departments/" + departmentId + "/roles",
        Map.of("userId", findByUsername(allDepartmentMembers, staffName).path("userId").asLong(), "role", "ADMIN")), 200);
    var afterRoleChange = result(request("admin", "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members?size=100", null), 200).path("items");
    assertThat(findByUsername(afterRoleChange, staffName).path("joinedAt").asText()).isEqualTo(staffJoinedAt);

    var departmentAdminRoster = result(request(staffName, "GET",
        "/api/v1/workspaces/departments/" + departmentId + "/members", null), 200);
    assertThat(departmentAdminRoster.path("items").isArray()).isTrue();
    request(staffName, "GET", "/api/v1/workspaces/hospitals/" + hospitalId + "/members", null)
        .andExpect(status().isForbidden());

    var firstHospitalPage = result(request("admin", "GET",
        "/api/v1/workspaces/hospitals/" + hospitalId + "/members?page=0&size=1", null), 200);
    assertThat(firstHospitalPage.path("items").size()).isEqualTo(1);
    assertThat(firstHospitalPage.path("totalElements").asLong()).isEqualTo(4L);
    assertThat(firstHospitalPage.path("hasNext").asBoolean()).isTrue();
    var hospitalPage = result(request("admin", "GET",
        "/api/v1/workspaces/hospitals/" + hospitalId + "/members?size=100", null), 200);
    assertThat(hospitalPage.path("totalElements").asLong()).isEqualTo(4L);
    assertThat(hospitalPage.path("items").findValuesAsText("role")).contains("OWNER", "MEMBER");
    var hospitalDoctor = findByUsername(hospitalPage.path("items"), doctorName);
    assertThat(hospitalDoctor.path("departments").get(0).path("doctorId").asLong())
        .isEqualTo(linked.path("doctorId").asLong());
    assertThat(hospitalDoctor.path("departments").get(0).path("joinedAt").isNull()).isFalse();
    assertThat(findByUsername(hospitalPage.path("items"), disabledName).path("enabled").asBoolean()).isFalse();
    assertThat(hospitalPage.path("items").toString()).doesNotContain("passwordHash", "sessionStamp", "patientId", "email");

    long activeDepartmentId = result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .path("activeDepartmentId").asLong();
    Long seededHospitalId = jdbc.queryForObject(
        "select hospital_id from departments where id=?", Long.class, activeDepartmentId);
    Long adminId = users.findByUsername("admin").orElseThrow().getId();
    jdbc.update("update hospital_memberships set joined_at=null where hospital_id=? and user_id=?",
        seededHospitalId, adminId);
    var seededHospitalMembers = result(request("admin", "GET",
        "/api/v1/workspaces/hospitals/" + seededHospitalId + "/members?size=100", null), 200).path("items");
    assertThat(findByUsername(seededHospitalMembers, "admin").path("joinedAt").isNull()).isTrue();

    request(doctorName, "GET", "/api/v1/workspaces/departments/" + departmentId + "/members", null)
        .andExpect(status().isForbidden());
    request(doctorName, "GET", "/api/v1/workspaces/hospitals/" + hospitalId + "/members", null)
        .andExpect(status().isForbidden());

    String otherOwnerName = "otherowner" + unique();
    createMember(otherOwnerName, true);
    result(request(otherOwnerName, "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Other hospital " + unique(), "departmentName", "Other ward")), 201);
    request(otherOwnerName, "GET", "/api/v1/workspaces/hospitals/" + hospitalId + "/members", null)
        .andExpect(status().isForbidden());
    request(otherOwnerName, "GET", "/api/v1/workspaces/departments/" + departmentId + "/members", null)
        .andExpect(status().isForbidden());
  }

  private JsonNode createMember(String username, boolean enabled) throws Exception {
    return result(request("admin", "POST", "/api/v1/users", Map.of(
        "username", username, "password", "RosterPassword123!", "role", "MEDICAL_STAFF", "enabled", enabled)), 201);
  }

  private void joinDepartment(String username, String code) throws Exception {
    result(request(username, "POST", "/api/v1/workspaces/join", Map.of("code", code)), 200);
  }

  private JsonNode findByUsername(JsonNode members, String username) {
    for (var member : members) if (username.equals(member.path("username").asText())) return member;
    throw new AssertionError("Roster member not found: " + username);
  }
}
