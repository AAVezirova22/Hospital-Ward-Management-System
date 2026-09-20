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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "server.servlet.session.cookie.secure=false"
    })
@AutoConfigureMockMvc
class WorkspaceIsolationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    HospitalIntegrationTest.database(registry);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  ResultActions call(String who, String method, String path, Object body, Long department)
      throws Exception {
    MockHttpServletRequestBuilder request =
        "POST".equals(method) ? post(path) : get(path);
    request.with(user(who)).with(csrf());
    if (department != null) request.header("X-Department-Id", department);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  JsonNode body(ResultActions actions, int status) throws Exception {
    return json.readTree(
        actions.andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
  }

  String unique() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
