package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DemoStatusTest extends HospitalSupport {
  @Test
  void statusLabelsTheEnvironmentAndKeepsDemoResetOffOutsideDemo() throws Exception {
    mvc.perform(get("/api/v1/demo/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value("development"))
        .andExpect(jsonPath("$.enabled").value(false));
  }
}
