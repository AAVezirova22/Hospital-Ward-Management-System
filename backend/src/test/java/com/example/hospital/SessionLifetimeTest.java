package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SessionLifetimeTest extends HospitalSupport {
  private MockHttpSession signIn() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", "admin")
                    .param("password", "IntegrationPassword123!"))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  void signInRecordsTheAuthenticationTime() throws Exception {
    var session = signIn();
    assertThat(session.getAttribute("authenticatedAt")).isInstanceOf(Long.class);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
  }

  @Test
  void sessionsOlderThanTheMaximumMustSignInAgain() throws Exception {
    var session = signIn();
    session.setAttribute(
        "authenticatedAt", Instant.now().minus(Duration.ofHours(13)).toEpochMilli());

    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    assertThat(session.isInvalid()).isTrue();

    var fresh = signIn();
    mvc.perform(get("/api/v1/auth/me").session(fresh)).andExpect(status().isOk());
  }
}
