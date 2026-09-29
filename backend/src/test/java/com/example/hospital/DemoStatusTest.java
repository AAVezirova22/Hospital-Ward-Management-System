package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * The environment label as a client outside a demo deployment sees it.
 *
 * <p>The label is read from the {@code app.environment} property, and this class states it
 * explicitly rather than relying on the default. It inherited the base class's properties,
 * which do not set it, so the value it asserted was whichever context surefire happened to
 * build first — {@code DemoOperationsIntegrationTest} sets {@code app.environment=demo}, and
 * depending on class order this test saw {@code demo} where it expected {@code development}.
 * It failed in CI and passed locally, in both directions across runs.
 *
 * <p>The base class's properties are repeated rather than merged: a subclass's
 * {@code @SpringBootTest} attributes replace the inherited ones instead of adding to them.
 */
@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "app.ai.rate-limit=10000",
      "app.rate-limits.enabled=false",
      "server.servlet.session.cookie.secure=false",
      "app.environment=development"
    })
@AutoConfigureMockMvc
class DemoStatusTest extends HospitalSupport {
  @Test
  void statusLabelsTheEnvironmentAndKeepsDemoResetOffOutsideDemo() throws Exception {
    mvc.perform(get("/api/v1/demo/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value("development"))
        .andExpect(jsonPath("$.enabled").value(false));
  }
}
