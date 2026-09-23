package com.example.hospital;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "app.ai.rate-limit=10000",
      "server.servlet.session.cookie.secure=false"
    })
@AutoConfigureMockMvc
abstract class HospitalSupport {
  static PostgreSQLContainer<?> container;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("TEST_DATABASE_URL");
    if (url == null) {
      container = new PostgreSQLContainer<>("postgres:17-alpine");
      container.start();
      r.add("spring.datasource.url", container::getJdbcUrl);
      r.add("spring.datasource.username", container::getUsername);
      r.add("spring.datasource.password", container::getPassword);
    } else {
      r.add("spring.datasource.url", () -> url);
      r.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "postgres"));
      r.add(
          "spring.datasource.password",
          () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "postgres"));
    }
    if ("true".equals(System.getenv("TEST_PGLITE"))) {
      r.add("spring.flyway.postgresql.transactional-lock", () -> false);
      r.add("spring.datasource.hikari.maximum-pool-size", () -> 1);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AppUserRepository users;
  @Autowired RoomAssignmentRepository assignments;
  @Autowired AdmissionRepository admissions;
  @Autowired JdbcTemplate jdbc;

  long activeAssignments(Long roomId) {
    return jdbc.queryForObject(
        "select count(*) from room_assignments where room_id = ? and released_at is null",
        Long.class,
        roomId);
  }

  long assignmentCount(Long admissionId) {
    return jdbc.queryForObject(
        "select count(*) from room_assignments where admission_id = ?",
        Long.class,
        admissionId);
  }

  String unique() {
    return UUID.randomUUID().toString().substring(0, 10);
  }

  ResultActions request(String who, String method, String path, Object body) throws Exception {
    return request(who, method, path, body, null);
  }

  ResultActions request(String who, String method, String path, Object body, Long departmentId) throws Exception {
    MockHttpServletRequestBuilder b =
        switch (method) {
          case "POST" -> post(path);
          case "PUT" -> put(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    b.with(user(who)).with(csrf());
    if (departmentId != null) b.header("X-Department-Id", departmentId);
    if (body != null)
      b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(b);
  }

  JsonNode result(ResultActions r, int status) throws Exception {
    return json.readTree(
        r.andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
  }

  JsonNode createPatient() throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/patients",
            Map.of(
                "patientIdentifier",
                "TEST-" + unique(),
                "firstName",
                "Test" + unique(),
                "lastName",
                "Patient",
                "dateOfBirth",
                "1980-01-01")),
        201);
  }

  JsonNode room(int beds) throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/rooms",
            Map.of("roomNumber", "T-" + unique(), "bedCount", beds, "active", true)),
        201);
  }

  JsonNode roomCapacity(long roomId) throws Exception {
    var rooms = result(request("admin", "GET", "/api/v1/rooms", null), 200);
    for (var room : rooms) if (room.path("id").asLong() == roomId) return room;
    throw new AssertionError("Room capacity is not visible in the current department.");
  }

  int occupiedBeds(long roomId) throws Exception {
    return roomCapacity(roomId).path("occupiedBeds").asInt();
  }

  JsonNode admit(JsonNode p, JsonNode r) throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId", p.get("id").asLong(), "doctorId", 1, "roomId", r.get("id").asLong())),
        201);
  }

  JsonNode ai(String who, String msg, Long selected) throws Exception {
    Map<String, Object> b = new HashMap<>();
    b.put("message", msg);
    if (selected != null) b.put("selectedPatientId", selected);
    return result(request(who, "POST", "/api/v1/assistant/messages", b), 200);
  }
}
