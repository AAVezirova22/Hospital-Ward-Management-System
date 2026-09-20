package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "server.servlet.session.cookie.secure=false"
    })
@AutoConfigureMockMvc
class WorkspaceIsolationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    HospitalIntegrationTest.database(registry);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  ResultActions call(String who, String method, String path, Object body, Long department)
      throws Exception {
    MockHttpServletRequestBuilder request =
        "POST".equals(method) ? post(path) : get(path);
    request.with(user(who)).with(csrf());
    if (department != null) request.header("X-Department-Id", department);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  JsonNode body(ResultActions actions, int status) throws Exception {
    return json.readTree(
        actions.andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
  }

  String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void recordsStayInsideTheSelectedDepartment() throws Exception {
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "North Clinic " + unique(), "departmentName", "Cardiology"),
                1L),
            201);
    long other = created.get("departmentId").asLong();
    assertThat(jdbc.queryForObject("select count(*) from workflow_lock where id=?", Integer.class, other))
        .isEqualTo(1);
    var homePatient =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/patients",
                Map.of(
                    "patientIdentifier",
                    "HOME-" + unique(),
                    "firstName",
                    "Home",
                    "lastName",
                    "Patient",
                    "dateOfBirth",
                    "1980-01-01"),
                1L),
            201);
    var otherPatient =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/patients",
                Map.of(
                    "patientIdentifier",
                    "AWAY-" + unique(),
                    "firstName",
                    "Away",
                    "lastName",
                    "Patient",
                    "dateOfBirth",
                    "1981-02-02"),
                other),
            201);
    var home = body(call("admin", "GET", "/api/v1/patients", null, 1L), 200);
    var away = body(call("admin", "GET", "/api/v1/patients", null, other), 200);
    assertThat(home.toString()).contains(homePatient.get("patientIdentifier").asText());
    assertThat(home.toString()).doesNotContain(otherPatient.get("patientIdentifier").asText());
    assertThat(away.toString()).contains(otherPatient.get("patientIdentifier").asText());
    assertThat(away.toString()).doesNotContain(homePatient.get("patientIdentifier").asText());
  }

  @Test
  void reportsStayInsideTheSelectedDepartment() throws Exception {
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Report Clinic " + unique(), "departmentName", "Imaging"),
                1L),
            201);
    long other = created.get("departmentId").asLong();
    var home = body(call("admin", "GET", "/api/v1/reports/dashboard", null, 1L), 200);
    var away = body(call("admin", "GET", "/api/v1/reports/dashboard", null, other), 200);
    assertThat(away.get("activeAdmissions").asInt()).isZero();
    assertThat(away.get("totalBeds").asInt()).isZero();
    assertThat(home.get("totalBeds").asInt()).isGreaterThan(away.get("totalBeds").asInt());
  }

  @Test
  void teamAccessListsOnlyLocalDepartmentMembers() throws Exception {
    String name = "staff" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                name,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Staff Clinic " + unique(), "departmentName", "Surgery"),
                1L),
            201);
    long other = created.get("departmentId").asLong();
    var home = body(call("admin", "GET", "/api/v1/users", null, 1L), 200);
    var away = body(call("admin", "GET", "/api/v1/users", null, other), 200);
    assertThat(home.toString()).contains(name);
    assertThat(away.toString()).doesNotContain(name);
  }

  @Test
  void joiningWithACodeNeverGrantsAdministratorRights() throws Exception {
    String name = "join" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                name,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Join Clinic " + unique(), "departmentName", "Neurology"),
                1L),
            201);
    long other = created.get("departmentId").asLong();
    String code = created.get("departmentCode").asText();
    assertThat(code).isNotBlank();
    body(call(name, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L), 200);
    call(name, "GET", "/api/v1/users", null, other).andExpect(status().isForbidden());
    call(name, "GET", "/api/v1/auth/me", null, other)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MEDICAL_STAFF"));
    var team = body(call("admin", "GET", "/api/v1/users", null, other), 200);
    assertThat(team.toString()).contains(name);
    assertThat(team.toString()).contains("MEDICAL_STAFF");
  }

  @Test
  void unknownDepartmentHeadersAreRejected() throws Exception {
    call("admin", "GET", "/api/v1/patients", null, 999999L)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEPARTMENT_ACCESS_DENIED"));
  }

  @Test
  void invalidJoinCodesAreRejected() throws Exception {
    call("admin", "POST", "/api/v1/workspaces/join", Map.of("code", "not-a-code"), 1L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CODE"));
  }

  @Test
  void joinCodeGuessesAreRateLimited() throws Exception {
    String guess = "AAAAAAAAAAAAAAAAAAAAAAAA";
    for (int i = 0; i < 5; i++) {
      call("admin", "POST", "/api/v1/workspaces/join", Map.of("code", guess), 1L)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }
    call("admin", "POST", "/api/v1/workspaces/join", Map.of("code", guess), 1L)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void hospitalJoinDoesNotOpenDepartmentRecords() throws Exception {
    String name = "hosp" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                name,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Code Clinic " + unique(), "departmentName", "Oncology"),
                1L),
            201);
    long hospitalId = created.get("hospitalId").asLong();
    long departmentId = created.get("departmentId").asLong();
    assertThat(hospitalId).isPositive();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/patients",
            Map.of(
                "patientIdentifier",
                "CODE-" + unique(),
                "firstName",
                "Hidden",
                "lastName",
                "Record",
                "dateOfBirth",
                "1977-07-07"),
            departmentId),
        201);
    String code = created.get("hospitalCode").asText();
    body(call(name, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L), 200);
    call(name, "GET", "/api/v1/patients", null, departmentId)
        .andExpect(status().isForbidden());
  }

  @Test
  void staffCanLeaveADepartmentAndOwnersCannotAbandonTheHospital() throws Exception {
    String name = "leave" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                name,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Leave Clinic " + unique(), "departmentName", "Ward"),
                1L),
            201);
    long hospitalId = created.get("hospitalId").asLong();
    long departmentId = created.get("departmentId").asLong();
    String code = created.get("departmentCode").asText();
    body(call(name, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L), 200);
    body(call(name, "POST", "/api/v1/workspaces/departments/" + departmentId + "/leave", Map.of(), 1L), 200);
    call(name, "GET", "/api/v1/patients", null, departmentId).andExpect(status().isForbidden());
    call("admin", "POST", "/api/v1/workspaces/hospitals/" + hospitalId + "/leave", Map.of(), 1L)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_OWNER"));
  }

  @Test
  void hospitalOwnerCanGrantASecondOwner() throws Exception {
    String name = "own" + unique();
    var createdUser =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/users",
                Map.of(
                    "username",
                    name,
                    "password",
                    "UserPassword123!",
                    "role",
                    "MEDICAL_STAFF",
                    "enabled",
                    true),
                1L),
            201);
    long userId = createdUser.get("id").asLong();
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Owner Clinic " + unique(), "departmentName", "Admin"),
                1L),
            201);
    long hospitalId = created.get("hospitalId").asLong();
    String code = created.get("hospitalCode").asText();
    body(call(name, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L), 200);
    body(call("admin", "POST", "/api/v1/workspaces/hospitals/" + hospitalId + "/owners", Map.of("userId", userId), 1L), 200);
    var after = body(call(name, "GET", "/api/v1/workspaces", null, 1L), 200);
    boolean owner = false;
    for (JsonNode hospital : after.get("hospitals")) {
      if (hospital.get("id").asLong() == hospitalId) owner = hospital.get("owner").asBoolean();
    }
    assertThat(owner).isTrue();
  }

  @Test
  void auditHistoryStaysInsideTheSelectedDepartment() throws Exception {
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Audit Clinic " + unique(), "departmentName", "Records"),
                1L),
            201);
    long other = created.get("departmentId").asLong();
    var patient =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/patients",
                Map.of(
                    "patientIdentifier",
                    "AUD-" + unique(),
                    "firstName",
                    "Audit",
                    "lastName",
                    "Only",
                    "dateOfBirth",
                    "1975-05-05"),
                other),
            201);
    var home = body(call("admin", "GET", "/api/v1/audit", null, 1L), 200);
    var away = body(call("admin", "GET", "/api/v1/audit", null, other), 200);
    assertThat(away.toString()).contains(patient.get("id").asText());
    assertThat(home.toString()).doesNotContain("\"entityId\":" + patient.get("id").asLong());
  }

  @Test
  void expiredJoinCodesAreRejected() throws Exception {
    String name = "exp" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                name,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Expiry Clinic " + unique(), "departmentName", "Triage"),
                1L),
            201);
    long departmentId = created.get("departmentId").asLong();
    String code = created.get("departmentCode").asText();
    jdbc.update(
        "update departments set join_code_expires_at = now() - interval '1 hour' where id=?",
        departmentId);
    call(name, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
  }

  @Test
  void singleUseJoinCodesRotateAfterTheFirstJoin() throws Exception {
    String first = "once" + unique();
    String second = "twice" + unique();
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                first,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    body(
        call(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                second,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true),
            1L),
        201);
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Once Clinic " + unique(), "departmentName", "Intake"),
                1L),
            201);
    long departmentId = created.get("departmentId").asLong();
    var rotated =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/departments/" + departmentId + "/code",
                Map.of("expiresInHours", 24, "singleUse", true),
                1L),
            200);
    String code = rotated.get("code").asText();
    body(call(first, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L), 200);
    call(second, "POST", "/api/v1/workspaces/join", Map.of("code", code), 1L)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CODE"));
  }

  @Test
  void workspaceListOmitsLiveJoinCodes() throws Exception {
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Reveal Clinic " + unique(), "departmentName", "Codes"),
                1L),
            201);
    long hospitalId = created.get("hospitalId").asLong();
    long departmentId = created.get("departmentId").asLong();
    var listed = body(call("admin", "GET", "/api/v1/workspaces", null, 1L), 200);
    assertThat(listed.toString()).doesNotContain(created.get("hospitalCode").asText());
    assertThat(listed.toString()).doesNotContain(created.get("departmentCode").asText());
    boolean hospitalFlag = false;
    boolean departmentFlag = false;
    for (JsonNode hospital : listed.get("hospitals")) {
      if (hospital.get("id").asLong() == hospitalId) {
        hospitalFlag = hospital.get("hasJoinCode").asBoolean();
        for (JsonNode department : hospital.get("departments")) {
          if (department.get("id").asLong() == departmentId)
            departmentFlag = department.get("hasJoinCode").asBoolean();
        }
      }
    }
    assertThat(hospitalFlag).isTrue();
    assertThat(departmentFlag).isTrue();
    var hospitalCode =
        body(call("admin", "GET", "/api/v1/workspaces/hospitals/" + hospitalId + "/code", null, 1L), 200);
    var departmentCode =
        body(
            call("admin", "GET", "/api/v1/workspaces/departments/" + departmentId + "/code", null, 1L),
            200);
    assertThat(hospitalCode.get("code").asText()).isEqualTo(created.get("hospitalCode").asText());
    assertThat(departmentCode.get("code").asText()).isEqualTo(created.get("departmentCode").asText());
  }

  @Test
  void ownerCanGrantDoctorAccessInADepartment() throws Exception {
    String name = "doc" + unique();
    var createdUser =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/users",
                Map.of(
                    "username",
                    name,
                    "password",
                    "UserPassword123!",
                    "role",
                    "MEDICAL_STAFF",
                    "enabled",
                    true),
                1L),
            201);
    long userId = createdUser.get("id").asLong();
    var created =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Doctor Clinic " + unique(), "departmentName", "Wards"),
                1L),
            201);
    long departmentId = created.get("departmentId").asLong();
    body(
        call(
            name,
            "POST",
            "/api/v1/workspaces/join",
            Map.of("code", created.get("departmentCode").asText()),
            1L),
        200);
    var granted =
        body(
            call(
                "admin",
                "POST",
                "/api/v1/workspaces/departments/" + departmentId + "/roles",
                Map.of("userId", userId, "role", "DOCTOR"),
                1L),
            200);
    assertThat(granted.get("role").asText()).isEqualTo("DOCTOR");
    assertThat(granted.get("doctorId").isNull()).isFalse();
    call(name, "GET", "/api/v1/auth/me", null, departmentId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("DOCTOR"))
        .andExpect(jsonPath("$.doctorId").value(granted.get("doctorId").asLong()));
  }
}
