package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class HospitalAuthTest extends HospitalSupport {
  @Test
  void authenticationAndCsrfAreRequired() throws Exception {
    mvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/patients")
                .with(user("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString());
  }

  @Test
  void actualLoginLogoutAndPasswordHashing() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "wrong"))
        .andExpect(status().isUnauthorized());
    var ok =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", "admin")
                    .param("password", "IntegrationPassword123!"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
    var session = (org.springframework.mock.web.MockHttpSession) ok.getRequest().getSession(false);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    assertThat(users.findByUsername("admin").orElseThrow().getPasswordHash())
        .startsWith("$2a$")
        .doesNotContain("IntegrationPassword");
  }

  @Test
  void loginFailuresFromOneSourceDoNotLockOutTheSameAccountFromAnotherSource()
      throws Exception {
    String username = "backoff" + unique();
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

    String attackerAddress = "203.0.113.10";
    String userAddress = "198.51.100.20";
    for (int attempt = 0; attempt < 5; attempt++)
      loginFrom(username, "WrongPassword123!", attackerAddress)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    loginFrom(username, "UserPassword123!", userAddress)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(username));
    loginFrom(username, "UserPassword123!", attackerAddress)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void disablingUserRevokesExistingAuthentication() throws Exception {
    String name = "u" + unique();
    var u =
        result(
            request(
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
                    true)),
            201);
    assertThat(u.has("passwordHash")).isFalse();
    request(name, "GET", "/api/v1/patients", null).andExpect(status().isOk());
    request(
            "admin",
            "PUT",
            "/api/v1/users/" + u.get("id").asLong(),
            Map.of("username", name, "role", "MEDICAL_STAFF", "enabled", false, "version", 0))
        .andExpect(status().isOk());
    request(name, "GET", "/api/v1/patients", null).andExpect(status().isUnauthorized());
  }

  @Test
  void selfLockoutAndUnlinkedDoctorAreRejected() throws Exception {
    var u = users.findByUsername("admin").orElseThrow();
    request(
            "admin",
            "PUT",
            "/api/v1/users/" + u.getId(),
            Map.of("username", "admin", "role", "ADMIN", "enabled", false, "version", u.getVersion()))
        .andExpect(status().isConflict());
    request(
            "admin",
            "POST",
            "/api/v1/users",
            Map.of(
                "username",
                "u" + unique(),
                "password",
                "TestPassword123!",
                "role",
                "DOCTOR",
                "enabled",
                true))
        .andExpect(status().isBadRequest());
  }

  private ResultActions loginFrom(String username, String password, String sourceAddress)
      throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .with(csrf())
            .with(
                request -> {
                  request.setRemoteAddr(sourceAddress);
                  return request;
                })
            .param("username", username)
            .param("password", password));
  }
}
