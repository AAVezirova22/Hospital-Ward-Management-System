package com.example.hospital;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.hospital.repository.*;
import com.example.hospital.service.ConfirmationEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"app.seed=true","app.bootstrap-password=IntegrationPassword123!","app.registration.enabled=true","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
class RegistrationIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){HospitalSupport.database(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired AppUserRepository users; @Autowired JdbcTemplate jdbc;
 @MockitoBean ConfirmationEmailService email;
 @BeforeEach void setup(){when(email.configured()).thenReturn(true);}
 String signup(String requestedRole) throws Exception {
   String name="signup_"+UUID.randomUUID().toString().substring(0,8);
   var body=Map.of("username",name,"email",name+"@example.test","password","RegistrationPassword123!","firstName","Maya","lastName","Koleva","dateOfBirth","1994-03-12","requestedRole",requestedRole,"hospitalId",1);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
   return name;
 }
 String capturedToken(){var token=ArgumentCaptor.forClass(String.class);verify(email).send(anyString(),anyString(),token.capture(),anyBoolean(),anyInt());return token.getValue();}
 @Test void patientMustVerifyAndCannotAccessStaffEndpoints() throws Exception {
   String name=signup("PATIENT"),token=capturedToken();var before=users.findByUsername(name).orElseThrow();assertThat(before.isEnabled()).isFalse();assertThat(before.getRole()).isEqualTo("PATIENT");
   assertThat(jdbc.queryForObject("select token_hash from email_verifications where user_id=?",String.class,before.getId())).doesNotContain(token).hasSize(64);
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isOk());
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isBadRequest());
   mvc.perform(get("/api/v1/portal/me").with(user(name))).andExpect(status().isOk()).andExpect(jsonPath("$.patient.firstName").value("Maya"));
   for(String path:List.of("/patients","/admissions","/rooms","/users","/reports/operations","/planner/simulate?arrivals=3")) mvc.perform(get("/api/v1"+path).with(user(name))).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/assistant/messages").with(user(name)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"show department status\"}")).andExpect(status().isForbidden());
 }
 @Test void doctorEmailVerificationNeverGrantsDoctorRole() throws Exception {
   String name=signup("DOCTOR"),token=capturedToken();
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isOk());
   var u=users.findByUsername(name).orElseThrow();assertThat(u.isEmailVerified()).isTrue();assertThat(u.getRole()).isEqualTo("PATIENT");assertThat(u.getRequestedRole()).isEqualTo("DOCTOR");assertThat(u.getDoctorId()).isNull();
 }
 @Test void expiredAndMissingCsrfLinksCannotActivateAccount() throws Exception {
   String name=signup("PATIENT"),token=capturedToken();jdbc.update("update email_verifications set expires_at=now()-interval '1 minute' where user_id=?",users.findByUsername(name).orElseThrow().getId());
   String body=json.writeValueAsString(Map.of("token",token));
   mvc.perform(post("/api/v1/registration/verify").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
   assertThat(users.findByUsername(name).orElseThrow().isEnabled()).isFalse();
 }
 @Test void deliveryFailureRollsBackNewAccount() throws Exception {
   doThrow(new com.example.hospital.api.ApiException(503,"EMAIL_UNAVAILABLE","Delivery unavailable")).when(email).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
   String name="failure_"+UUID.randomUUID().toString().substring(0,8);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.test","password","RegistrationPassword123!","firstName","Maya","lastName","Koleva","dateOfBirth","1994-03-12","requestedRole","PATIENT","hospitalId",1)))).andExpect(status().isServiceUnavailable());
   assertThat(users.findByUsername(name)).isEmpty();
 }
 @Test void signupAttachesThePatientToTheChosenHospital() throws Exception {
   var created=json.readTree(mvc.perform(post("/api/v1/workspaces/hospitals").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Signup Clinic\",\"departmentName\":\"Intake\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
   long hospitalId=created.get("hospitalId").asLong();
   long departmentId=created.get("departmentId").asLong();
   String name="hosp_"+UUID.randomUUID().toString().substring(0,8);
   var body=new java.util.LinkedHashMap<String,Object>();
   body.put("username",name); body.put("email",name+"@example.test"); body.put("password","RegistrationPassword123!");
   body.put("firstName","Maya"); body.put("lastName","Koleva"); body.put("dateOfBirth","1994-03-12");
   body.put("requestedRole","PATIENT"); body.put("hospitalId",hospitalId);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
   Long patientId=users.findByUsername(name).orElseThrow().getPatientId();
   assertThat(jdbc.queryForObject("select department_id from patients where id=?", Long.class, patientId)).isEqualTo(departmentId);
 }
}
