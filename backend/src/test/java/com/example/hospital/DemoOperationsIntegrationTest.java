package com.example.hospital;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={"app.demo=true","app.demo-token=","app.demo-public-login=true","app.seed=true","app.bootstrap-password=IntegrationPassword123!","server.servlet.session.cookie.secure=false", "spring.flyway.default-schema=demo_verification", "spring.jpa.properties.hibernate.default_schema=demo_verification", "spring.datasource.hikari.connection-init-sql=SET search_path TO demo_verification"})
@AutoConfigureMockMvc
class DemoOperationsIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){HospitalSupport.database(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Test void telemetryIsAdministratorOnlyAndStreamRequiresStaffSession() throws Exception {
  mvc.perform(get("/api/v1/management/health")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/management/health").with(user("doctor"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/management/health").with(user("staff"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/management/health").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.components.db.status").value("UP"));
  mvc.perform(get("/api/v1/operations/stream")).andExpect(status().isUnauthorized());
  var result=mvc.perform(get("/api/v1/operations/stream").with(user("doctor"))).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
  assertThat(result.getResponse().getContentAsString()).contains("event:ready", "data:refresh").doesNotContain("patient", "admissionId");
  result.getRequest().getAsyncContext().complete();
 }
 @Test void demoLoginIsCsrfProtectedAndPreservesRequestedRole() throws Exception {
  org.springframework.test.web.servlet.request.RequestPostProcessor throughProxy = request -> {
   request.setRemoteAddr("198.51.100.17");
   request.addHeader("X-Forwarded-For", "127.0.0.1");
   return request;
  };
  mvc.perform(post("/api/v1/demo/login").with(throughProxy).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  var result=mvc.perform(post("/api/v1/demo/login").with(throughProxy).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"DOCTOR\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DOCTOR")).andReturn();
  var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DOCTOR"));
  mvc.perform(get("/api/v1/users").session(session)).andExpect(status().isForbidden());
 }
 @Test void simulationDoesNotChangeSavedCapacityAndRejectsUnboundedInput() throws Exception {
  var before=mvc.perform(get("/api/v1/rooms").with(user("admin"))).andReturn().getResponse().getContentAsString();
  var result=mvc.perform(get("/api/v1/planner/simulate?arrivals=100").with(user("admin"))).andExpect(status().isOk()).andReturn();
  var plan=json.readTree(result.getResponse().getContentAsString());assertThat(plan.get("placements").size()+plan.get("unplaced").asInt()).isEqualTo(100);
  var after=mvc.perform(get("/api/v1/rooms").with(user("admin"))).andReturn().getResponse().getContentAsString();assertThat(after).isEqualTo(before);
  mvc.perform(get("/api/v1/planner/simulate?arrivals=101").with(user("admin"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/reports/operations").with(user("doctor"))).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("Assigned admissions")).andExpect(jsonPath("$.trends.length()").value(14));
 }
 @Test void resetRequiresAdminAndExplicitConfirmationThenRecreatesScenario() throws Exception {
  mvc.perform(post("/api/v1/demo/reset").with(user("staff")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"RESET DEMO\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/demo/reset").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"reset\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/workspaces/hospitals").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Transient Clinic\",\"departmentName\":\"Overflow\"}")).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/demo/reset").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"RESET DEMO\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/patients").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(28));
  mvc.perform(get("/api/v1/reports/dashboard").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.activeAdmissions").value(14));
  var workspaces=json.readTree(mvc.perform(get("/api/v1/workspaces").with(user("admin"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertThat(workspaces.get("hospitals")).hasSize(1);
  assertThat(workspaces.get("hospitals").get(0).get("name").asText()).isEqualTo("Medcore Hospital");
  assertThat(workspaces.get("hospitals").get(0).get("id").asLong()).isEqualTo(1L);
 }
}
