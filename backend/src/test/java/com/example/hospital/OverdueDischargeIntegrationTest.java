package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "app.seed=true",
    "app.bootstrap-password=IntegrationPassword123!",
    "server.servlet.session.cookie.secure=false",
    "spring.flyway.schemas=overdue_verification",
    "spring.flyway.default-schema=overdue_verification",
    "spring.jpa.properties.hibernate.default_schema=overdue_verification",
    "spring.datasource.hikari.connection-init-sql=SET search_path TO overdue_verification"
})
@AutoConfigureMockMvc
class OverdueDischargeIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    HospitalSupport.database(registry);
    registry.add("spring.flyway.schemas", () -> "overdue_verification");
    registry.add("spring.flyway.default-schema", () -> "overdue_verification");
    registry.add("spring.flyway.create-schemas", () -> true);
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "overdue_verification");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void operationsWorklistAppliesDateAndDoctorVisibilityRules() throws Exception {
    var today = LocalDate.now(ZoneOffset.UTC);
    var departmentId = primaryDepartmentId();
    var assignedDoctorId = doctorId("doctor");
    var otherDoctorId = anotherDoctorId(departmentId, assignedDoctorId);
    long oneDay = insertAdmission(departmentId, assignedDoctorId, today.minusDays(1), "ACTIVE");
    long threeDays = insertAdmission(departmentId, assignedDoctorId, today.minusDays(3), "ACTIVE");
    long dueToday = insertAdmission(departmentId, assignedDoctorId, today, "ACTIVE");
    long future = insertAdmission(departmentId, assignedDoctorId, today.plusDays(2), "ACTIVE");
    long unscheduled = insertAdmission(departmentId, assignedDoctorId, null, "ACTIVE");
    long otherDoctor = insertAdmission(departmentId, otherDoctorId, today.minusDays(1), "ACTIVE");
    long discharged = insertAdmission(departmentId, assignedDoctorId, today.minusDays(4), "DISCHARGED");

    var adminWorklist = operations("admin", departmentId);
    assertThat(admissionIds(adminWorklist)).contains(
        threeDays, oneDay, otherDoctor);
    assertDaysOverdue(adminWorklist, threeDays, 3);
    assertDaysOverdue(adminWorklist, oneDay, 1);
    assertDaysOverdue(adminWorklist, otherDoctor, 1);
    assertThat(admissionIds(adminWorklist))
        .doesNotContain(dueToday, future, unscheduled, discharged);

    var doctorWorklist = operations("doctor", departmentId);
    assertThat(admissionIds(doctorWorklist)).contains(threeDays, oneDay).doesNotContain(otherDoctor);
    assertThat(doctorIds(doctorWorklist)).containsOnly(assignedDoctorId);
    assertThat(admissionIds(operations("staff", departmentId)))
        .containsExactlyElementsOf(admissionIds(adminWorklist));
  }

  @Test
  void operationsWorklistUsesTheSelectedDepartment() throws Exception {
    var primaryDepartmentId = primaryDepartmentId();
    var created = result(scopedRequest("admin", "POST", "/api/v1/workspaces/hospitals",
        primaryDepartmentId, Map.of("name", "Overdue Clinic " + unique(),
            "departmentName", "Overdue Ward")), 201);
    long departmentId = created.get("departmentId").asLong();
    long doctorId = insertDoctor(departmentId);
    long admissionId = insertAdmission(departmentId, doctorId,
        LocalDate.now(ZoneOffset.UTC).minusDays(1), "ACTIVE");

    assertThat(admissionIds(operations("admin", primaryDepartmentId)))
        .doesNotContain(admissionId);
    assertThat(admissionIds(operations("admin", departmentId)))
        .containsExactly(admissionId);
  }

  private long insertAdmission(long departmentId, long doctorId, LocalDate expectedDate, String state) {
    var patientId = insertPatient(departmentId);
    return jdbc.queryForObject("""
        insert into admissions(department_id, admission_number, patient_id, attending_doctor_id,
                               admission_date_time, status, created_by, expected_discharge_date)
        values (?, ?, ?, ?, now() - interval '1 day', ?, ?, ?)
        returning id
        """, Long.class, departmentId, "ADM-OVERDUE-" + unique(), patientId, doctorId,
        state, adminId(), expectedDate);
  }

  private JsonNode operations(String username, long departmentId) throws Exception {
    return result(scopedRequest(username, "GET", "/api/v1/reports/operations", departmentId, null), 200);
  }

  private ResultActions scopedRequest(String username, String method, String path,
      long departmentId, Object body) throws Exception {
    MockHttpServletRequestBuilder request = "POST".equals(method) ? post(path) : get(path);
    request.with(user(username)).with(csrf()).header("X-Department-Id", departmentId);
    if (body != null) request.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  private java.util.List<Long> admissionIds(JsonNode worklist) {
    var ids = new java.util.ArrayList<Long>();
    worklist.get("overdueDischarges").forEach(row -> ids.add(row.get("admissionId").asLong()));
    return ids;
  }

  private java.util.List<Long> doctorIds(JsonNode worklist) {
    var ids = new java.util.ArrayList<Long>();
    worklist.get("overdueDischarges").forEach(row -> ids.add(row.get("attendingDoctorId").asLong()));
    return ids;
  }

  private void assertDaysOverdue(JsonNode worklist, long admissionId, int days) {
    var matching = new java.util.ArrayList<JsonNode>();
    worklist.get("overdueDischarges").forEach(row -> {
      if (row.get("admissionId").asLong() == admissionId) matching.add(row);
    });
    assertThat(matching).hasSize(1);
    assertThat(matching.getFirst().get("daysOverdue").asInt()).isEqualTo(days);
    assertThat(matching.getFirst().get("patientName").asText()).isNotBlank();
    assertThat(matching.getFirst().get("admissionNumber").asText()).isNotBlank();
  }

  private JsonNode result(ResultActions actions, int status) throws Exception {
    return json.readTree(actions.andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
  }

  private long insertPatient(long departmentId) {
    return jdbc.queryForObject("""
        insert into patients(department_id, patient_identifier, first_name, last_name, date_of_birth)
        values (?, ?, 'Test', 'Patient', date '1980-01-01')
        returning id
        """, Long.class, departmentId, "OVERDUE-" + unique());
  }

  private long insertDoctor(long departmentId) {
    return jdbc.queryForObject("""
        insert into doctors(department_id, doctor_identifier, first_name, last_name, specialty, active)
        values (?, ?, 'Ward', 'Doctor', 'General', true)
        returning id
        """, Long.class, departmentId, "DOC-OVERDUE-" + unique());
  }

  private long primaryDepartmentId() throws Exception {
    return result(mvc.perform(get("/api/v1/workspaces").with(user("admin")).with(csrf())), 200)
        .get("activeDepartmentId").asLong();
  }

  private long doctorId(String username) {
    return jdbc.queryForObject("select doctor_id from app_users where username=?", Long.class, username);
  }

  private long anotherDoctorId(long departmentId, long excludedDoctorId) {
    return jdbc.queryForObject("select id from doctors where department_id=? and id<>? order by id limit 1",
        Long.class, departmentId, excludedDoctorId);
  }

  private long adminId() {
    return jdbc.queryForObject("select id from app_users where username='admin'", Long.class);
  }

  private String unique() {
    return UUID.randomUUID().toString().substring(0, 10);
  }
}
