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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Read-only archival for closed departments (#162).
 */
@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!"
})
@AutoConfigureMockMvc
class DepartmentArchiveIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) { HospitalSupport.database(registry); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  /** A hospital whose owner is the seeded admin, plus its first department. */
  private record Unit(long hospitalId, long departmentId) {}

  private Unit unit() throws Exception {
    String name = "Trust " + UUID.randomUUID().toString().substring(0, 8);
    JsonNode hospital = json.readTree(mvc.perform(body("/api/v1/workspaces/hospitals",
            Map.of("name", name, "departmentName", "Ward")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    return new Unit(hospital.path("hospitalId").asLong(), hospital.path("departmentId").asLong());
  }

  private void becomeOwner(long unitHospitalId) {
    // The creating admin is already owner; make it explicit so the test does not
    // depend on that side effect being stable.
    jdbc.update("update hospital_memberships set owner=true where hospital_id=? and user_id="
        + "(select id from app_users where username='admin')", unitHospitalId);
  }

  @Test
  void archiveBlocksNewAdmissionsButLeavesHistoryAndReportsReadable() throws Exception {
    Unit u = unit();
    becomeOwner(u.hospitalId());
    long[] dr = doctorAndRoom(u.departmentId());

    // A discharged admission: the history an archived unit must keep serving.
    long patientId = createPatient(u.departmentId());
    long admissionId = createAdmission(u.departmentId(), patientId, dr[0], "DISCHARGED");

    JsonNode archived = json.readTree(mvc.perform(
            adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/archive")
                .param("reason", "Ward closed after the renovation")
                .with(user("admin")).with(csrf()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
    assertThat(archived.path("archiveReason").asText()).isEqualTo("Ward closed after the renovation");
    assertThat(archived.path("archivedAt").asText()).isNotEmpty();
    assertThat(archived.path("archivedBy").asText()).isEqualTo("admin");

    // New admissions are refused. A valid doctor and room in this unit are used, so
    // the refusal is about the archive and not about a missing room.
    long newPatientId = createPatient(u.departmentId());
    mvc.perform(body("/api/v1/admissions", Map.of(
            "patientId", newPatientId, "doctorId", dr[0], "roomId", dr[1]))
        .with(user("admin")).with(csrf()).header("X-Department-Id", String.valueOf(u.departmentId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEPARTMENT_ARCHIVED"));
    assertThat(jdbc.queryForObject("select count(*) from admissions where department_id=?",
        Integer.class, u.departmentId())).isEqualTo(1);

    // The historical record is still there and still readable.
    assertThat(jdbc.queryForObject("select count(*) from admissions where id=?", Integer.class, admissionId))
        .isEqualTo(1);
    // Reports keep working over the archived unit's history.
    mvc.perform(get("/api/v1/reports/census")
            .with(user("admin")).header("X-Department-Id", String.valueOf(u.departmentId())))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/reports/procedures")
            .param("from", "2020-01-01").param("to", "2030-01-01")
            .with(user("admin")).header("X-Department-Id", String.valueOf(u.departmentId())))
        .andExpect(status().isOk());

    // Archiving and restoring are both audited, separately.
    assertThat(auditCount(u.departmentId(), "DEPARTMENT_ARCHIVED")).isEqualTo(1);
    assertThat(auditCount(u.departmentId(), "DEPARTMENT_RESTORED")).isZero();

    JsonNode restored = json.readTree(mvc.perform(
            adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/restore")
                .param("reason", "Reopened")
                .with(user("admin")).with(csrf()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(restored.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(restored.path("archivedAt").isNull()).isTrue();
    assertThat(auditCount(u.departmentId(), "DEPARTMENT_RESTORED")).isEqualTo(1);

    // And writes work again after restore.
    mvc.perform(body("/api/v1/admissions", Map.of(
            "patientId", newPatientId, "doctorId", dr[0], "roomId", dr[1]))
        .with(user("admin")).with(csrf()).header("X-Department-Id", String.valueOf(u.departmentId())))
        .andExpect(status().isCreated());
  }

  @Test
  void aUnitWithPatientsStillAdmittedCannotBeArchived() throws Exception {
    Unit u = unit();
    becomeOwner(u.hospitalId());
    long patientId = createPatient(u.departmentId());
    createAdmission(u.departmentId(), patientId, doctorAndRoom(u.departmentId())[0], "ACTIVE");

    // Archiving now would strand an admitted patient with no way to be discharged.
    mvc.perform(adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/archive")
            .param("reason", "Closing")
            .with(user("admin")).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPEN_ADMISSIONS"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1")));

    assertThat(departmentStatus(u.departmentId())).isEqualTo("ACTIVE");
    assertThat(auditCount(u.departmentId(), "DEPARTMENT_ARCHIVED")).isZero();
  }

  @Test
  void archivedUnitsRefuseNewMembersIncludingThroughAJoinCode() throws Exception {
    Unit u = unit();
    becomeOwner(u.hospitalId());
    String departmentCode = jdbc.queryForObject(
        "select join_code from departments where id=?", String.class, u.departmentId());
    mvc.perform(adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/archive")
            .param("reason", "Closed")
            .with(user("admin")).with(csrf()))
        .andExpect(status().isOk());

    // The code is kept for the audit trail, but it must stop working.
    assertThat(jdbc.queryForObject("select join_code from departments where id=?",
        String.class, u.departmentId())).isEqualTo(departmentCode);

    String outsider = "outsider_" + UUID.randomUUID().toString().substring(0, 8);
    createUser(outsider);
    MvcResult join = mvc.perform(body("/api/v1/workspaces/join",
            Map.of("code", departmentCode))
            .with(user(outsider)).with(csrf()))
        .andExpect(status().isConflict())
        .andReturn();
    assertThat(json.readTree(join.getResponse().getContentAsString()).path("code").asText())
        .isEqualTo("DEPARTMENT_ARCHIVED");
    assertThat(jdbc.queryForObject(
        "select count(*) from department_memberships m join app_users u on u.id=m.user_id"
            + " where m.department_id=? and u.username=?",
        Integer.class, u.departmentId(), outsider)).isZero();
  }

  @Test
  void onlyAHospitalOwnerMayArchiveOrRestore() throws Exception {
    Unit u = unit();
    becomeOwner(u.hospitalId());
    // A plain member of the hospital, not an owner.
    String member = "member_" + UUID.randomUUID().toString().substring(0, 8);
    createUser(member);
    jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?)",
        u.hospitalId(), userId(member));
    jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'ADMIN')",
        u.departmentId(), userId(member));

    String path = "/api/v1/workspaces/departments/" + u.departmentId() + "/archive";
    mvc.perform(post(path).param("reason", "Not mine to close")
            .with(user(member)).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HOSPITAL_OWNER_REQUIRED"));
    assertThat(departmentStatus(u.departmentId())).isEqualTo("ACTIVE");

    // A department admin cannot close the unit, but a hospital owner can.
    mvc.perform(post(path).param("reason", "Owner decision")
            .with(user("admin")).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/restore")
            .with(user(member)).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void archiveIsIdempotentOnlyInTheSenseThatRepeatsAreRefusedAndStateIsReported() throws Exception {
    Unit u = unit();
    becomeOwner(u.hospitalId());
    String path = "/api/v1/workspaces/departments/" + u.departmentId() + "/archive";

    mvc.perform(post(path).with(user("admin")).with(csrf())).andExpect(status().isOk());
    // A second archive is a conflict, not a silent no-op that loses the first reason.
    mvc.perform(post(path).param("reason", "Again").with(user("admin")).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_ARCHIVED"));
    mvc.perform(adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/restore")
            .with(user("admin")).with(csrf()))
        .andExpect(status().isOk());
    // Restoring an active unit is likewise refused.
    mvc.perform(adminPost("/api/v1/workspaces/departments/" + u.departmentId() + "/restore")
            .with(user("admin")).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_ARCHIVED"));

    mvc.perform(get("/api/v1/workspaces/departments/" + u.departmentId() + "/state")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    // Archived units are listed so a picker can label them read-only.
    mvc.perform(post(path).with(user("admin")).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/workspaces")
            .with(user("admin")).header("X-Department-Id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedDepartmentIds").isArray());
  }

  private String departmentStatus(long departmentId) {
    return jdbc.queryForObject("select status from departments where id=?", String.class, departmentId);
  }

  private int auditCount(long entityId, String eventType) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where entity_id=? and event_type=?",
        Integer.class, entityId, eventType);
  }

  private long createPatient(long departmentId) {
    return jdbc.queryForObject("""
        insert into patients(department_id,patient_identifier,first_name,last_name,date_of_birth,created_at,updated_at)
        values (?,?,?,?,?,now(),now()) returning id
        """, Long.class, departmentId, "ARCH-" + UUID.randomUUID(),
        "Test", "Patient", java.sql.Date.valueOf("1980-01-01"));
  }

  /** A doctor and a room in this unit, since ids from another department are not visible. */
  private long[] doctorAndRoom(long departmentId) {
    long doctorId = jdbc.queryForObject("""
        insert into doctors(department_id,doctor_identifier,first_name,last_name,specialty,active,created_at,updated_at)
        values (?,?,?,?,?,true,now(),now()) returning id
        """, Long.class, departmentId, "DOC-" + UUID.randomUUID(),
        "Test", "Doctor", "General");
    long roomId = jdbc.queryForObject("""
        insert into rooms(department_id,room_number,bed_count,active,created_at,updated_at)
        values (?,?,1,true,now(),now()) returning id
        """, Long.class, departmentId, "RM-" + UUID.randomUUID().toString().substring(0, 8));
    return new long[] {doctorId, roomId};
  }

  private long createAdmission(long departmentId, long patientId, long doctorId, String status) {
    return jdbc.queryForObject("""
        insert into admissions(department_id,patient_id,attending_doctor_id,admission_number,
          admission_date_time,status,created_by,created_at,updated_at)
        values (?,?,?,?,now(),?,(select id from app_users where username='admin'),now(),now()) returning id
        """, Long.class, departmentId, patientId, doctorId, "ADM-" + UUID.randomUUID(), status);
  }

  private void createUser(String username) {
    jdbc.update("""
        insert into app_users(username,password_hash,role,enabled,email_verified,requested_role)
        values (?, 'unused-test-password-hash', 'MEDICAL_STAFF', true, true, 'MEDICAL_STAFF')
        """, username);
  }

  private long userId(String username) {
    return jdbc.queryForObject("select id from app_users where username=?", Long.class, username);
  }

  private MockHttpServletRequestBuilder body(String path, Object payload) throws Exception {
    return post(path).with(user("admin")).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
  }

  private MockHttpServletRequestBuilder adminPost(String path) {
    return post(path).with(user("admin")).with(csrf());
  }
}
