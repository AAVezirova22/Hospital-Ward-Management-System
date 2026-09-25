package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "app.rate-limits.enabled=true",
      "app.rate-limits.auth=2/1m",
      "app.rate-limits.search=3/1m",
      "app.rate-limits.exports=1/10m"
    })
class ApiRateLimitIntegrationTest extends HospitalSupport {
  private String staffAccount() throws Exception {
    String name = "rl-" + unique();
    var body = new HashMap<String, Object>();
    body.put("username", name);
    body.put("password", "RateLimitPassword123!");
    body.put("role", "MEDICAL_STAFF");
    body.put("enabled", true);
    result(request("admin", "POST", "/api/v1/users", body), 201);
    return name;
  }

  @Test
  void searchesAreBudgetedPerAccount() throws Exception {
    String first = staffAccount();
    for (int remaining = 2; remaining >= 0; remaining--)
      mvc.perform(get("/api/v1/patients").with(user(first)))
          .andExpect(status().isOk())
          .andExpect(header().string("RateLimit-Limit", "3"))
          .andExpect(header().string("RateLimit-Remaining", String.valueOf(remaining)));
    mvc.perform(get("/api/v1/patients").with(user(first)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists("Retry-After"));

    mvc.perform(get("/api/v1/patients").with(user(staffAccount()))).andExpect(status().isOk());
    for (int i = 0; i < 5; i++)
      mvc.perform(get("/api/v1/auth/me").with(user(first))).andExpect(status().isOk());
  }

  @Test
  void signInAttemptsAreBudgetedPerClientAddressBeforeCredentialsAreChecked() throws Exception {
    String address =
        "198.18." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(1, 255);
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/auth/login")
                  .with(csrf())
                  .with(r -> { r.setRemoteAddr(address); return r; })
                  .param("username", "admin")
                  .param("password", "wrong-" + i))
          .andExpect(status().isUnauthorized());
    var limited =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .with(r -> { r.setRemoteAddr(address); return r; })
                    .param("username", "admin")
                    .param("password", "IntegrationPassword123!"))
            .andExpect(status().isTooManyRequests())
            .andReturn();
    assertThat(limited.getRequest().getSession(false) == null
            || limited.getRequest().getSession(false).getAttribute("accountId") == null)
        .as("a limited sign-in never authenticates")
        .isTrue();
  }

  @Test
  void exportsHaveTheirOwnSmallerBudget() throws Exception {
    String account = staffAccount();
    String csv = "/api/v1/reports/procedures.csv?from=2021-01-01&to=2021-01-02";
    mvc.perform(get(csv).with(user(account))).andExpect(status().isOk());
    mvc.perform(get(csv).with(user(account))).andExpect(status().isTooManyRequests());
    mvc.perform(get("/api/v1/reports/dashboard").with(user(account))).andExpect(status().isOk());
  }
}
