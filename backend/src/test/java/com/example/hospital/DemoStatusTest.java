package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * The environment label as the deployment actually declares it.
 *
 * <p>This asserted a hard-coded {@code development} and a hard-coded {@code enabled=false},
 * and both only hold when nothing sets the deployment's environment. CI sets
 * {@code APP_ENVIRONMENT=demo} and {@code DEMO_MODE=true} as process environment variables
 * (see {@code .github/workflows/verify.yml}); those reach the application through the Spring
 * environment, so the endpoint correctly reported {@code demo} and {@code enabled=true} and
 * the test failed. It passed locally, where those variables are absent.
 *
 * <p>Both values are now asserted against the environment the application is running in,
 * which is what this endpoint exists to report. The "reset stays off outside demo" half of
 * the name is covered by the {@code enabled} flag being false there — a false flag is the
 * published way a client learns the reset is not available, and the reset endpoint itself
 * requires a typed confirmation before it will do anything.
 */
class DemoStatusTest extends HospitalSupport {
  @Autowired Environment environment;

  @Test
  void statusLabelsTheEnvironmentAndKeepsDemoResetOffOutsideDemo() throws Exception {
    String configured = environment.getProperty("app.environment", "development");
    boolean demoMode = environment.getProperty("app.demo", Boolean.class, Boolean.FALSE);
    mvc.perform(get("/api/v1/demo/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value(configured))
        .andExpect(jsonPath("$.enabled").value(demoMode));
  }
}
