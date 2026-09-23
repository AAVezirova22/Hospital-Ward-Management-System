package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class WorkspaceJoinConcurrencyTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void singleUseDepartmentCodeGrantsAccessToOnlyOneConcurrentUser() throws Exception {
    assertConcurrentSingleUseCodeGrantsAccessToOneUser(false);
  }

  @Test
  void singleUseHospitalCodeGrantsAccessToOnlyOneConcurrentUser() throws Exception {
    assertConcurrentSingleUseCodeGrantsAccessToOneUser(true);
  }

  private void assertConcurrentSingleUseCodeGrantsAccessToOneUser(boolean hospital)
      throws Exception {
    String first = "joinfirst" + unique();
    String second = "joinsecond" + unique();
    createMedicalStaffUser(first);
    createMedicalStaffUser(second);

    var workspace =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Concurrent Clinic " + unique(), "departmentName", "Intake")),
            201);
    long workspaceId =
        workspace.get(hospital ? "hospitalId" : "departmentId").asLong();
    String pathSegment = hospital ? "hospitals" : "departments";
    var rotated =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/workspaces/" + pathSegment + "/" + workspaceId + "/code",
                Map.of("expiresInHours", 24, "singleUse", true)),
            200);
    String code = rotated.get("code").asText();

    List<MvcResult> responses = new ArrayList<>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      List<Future<MvcResult>> tasks = new ArrayList<>();
      for (var username : List.of(first, second))
        tasks.add(
            executor.submit(
                () -> {
                  start.await();
                  return request(
                          username,
                          "POST",
                          "/api/v1/workspaces/join",
                          Map.of("code", code))
                      .andReturn();
                }));
      start.countDown();
      for (var task : tasks) responses.add(task.get(20, TimeUnit.SECONDS));
    }

    assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList())
        .containsExactlyInAnyOrder(200, 400);
    var rejected =
        responses.stream()
            .filter(r -> r.getResponse().getStatus() == 400)
            .findFirst()
            .orElseThrow();
    assertThat(json.readTree(rejected.getResponse().getContentAsString()).get("code").asText())
        .isEqualTo("INVALID_CODE");

    assertThat(
            jdbc.queryForObject(
                "select count(*) from hospital_memberships m join app_users u on u.id=m.user_id where m.hospital_id=? and u.username in (?,?)",
                Integer.class,
                workspace.get("hospitalId").asLong(),
                first,
                second))
        .isEqualTo(1);
    if (!hospital)
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from department_memberships m join app_users u on u.id=m.user_id where m.department_id=? and u.username in (?,?)",
                  Integer.class,
                  workspaceId,
                  first,
                  second))
          .isEqualTo(1);
  }

  private void createMedicalStaffUser(String username) throws Exception {
    result(
        request(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                username,
                "password",
                "UserPassword123!",
                "role",
                "MEDICAL_STAFF",
                "enabled",
                true)),
        201);
  }
}
