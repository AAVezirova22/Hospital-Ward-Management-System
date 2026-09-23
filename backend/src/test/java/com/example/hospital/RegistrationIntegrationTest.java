package com.example.hospital;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.hospital.repository.*;
import com.example.hospital.service.ConfirmationEmailService;
import com.example.hospital.service.EmailOutboxDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"app.seed=true","app.bootstrap-password=IntegrationPassword123!","app.registration.enabled=true","server.servlet.session.cookie.secure=false","app.email.outbox.poll-interval-ms=3600000","app.email.outbox.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class RegistrationIntegrationTest {
 private static final String RECOVERY_MESSAGE="If the registration can be recovered, a fresh confirmation email will be sent.";
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){HospitalSupport.database(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired AppUserRepository users; @Autowired JdbcTemplate jdbc; @Autowired EmailOutboxDispatcher dispatcher;
 @MockitoBean ConfirmationEmailService email;
 @MockitoBean WorkflowLockRepository workflowLocks;
 @BeforeEach void setup(){jdbc.update("delete from email_outbox");when(email.configured()).thenReturn(true);}
 String signup(String requestedRole) throws Exception {
   String name="signup_"+UUID.randomUUID().toString().substring(0,8);
   signup(name,name+"@example.test",requestedRole);
   return name;
 }
 void signup(String name,String address,String requestedRole) throws Exception {
   var body=Map.of("username",name,"email",address,"password","RegistrationPassword123!","firstName","Maya","lastName","Koleva","dateOfBirth","1994-03-12","requestedRole",requestedRole,"hospitalId",1);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
   dispatcher.dispatchDue();
 }
 String capturedToken(){var token=ArgumentCaptor.forClass(String.class);verify(email).send(anyString(),anyString(),token.capture(),anyBoolean(),anyInt());return token.getValue();}
 org.springframework.test.web.servlet.ResultActions recover(String username,String password,String address,String remoteAddress) throws Exception {
   return recover(username,password,address,remoteAddress,null);
 }
 org.springframework.test.web.servlet.ResultActions recover(String username,String password,String address,String remoteAddress,String forwardedFor) throws Exception {
   var body=Map.of("username",username,"password",password,"email",address);
   var result=mvc.perform(post("/api/v1/registration/recover").with(csrf()).with(r->{r.setRemoteAddr(remoteAddress);if(forwardedFor!=null)r.addHeader("X-Forwarded-For",forwardedFor);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
   dispatcher.dispatchDue();
   return result;
 }
 void assertRecoveryMessage(String username,String password,String address,String remoteAddress) throws Exception {
   recover(username,password,address,remoteAddress).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(RECOVERY_MESSAGE));
 }
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
 @Test void verificationBeforeDeliveryCancelsAndClearsTheQueuedSecret() throws Exception {
   String name="queued_"+UUID.randomUUID().toString().substring(0,8);
   var body=Map.of("username",name,"email",name+"@example.test","password","RegistrationPassword123!","firstName","Maya","lastName","Koleva","dateOfBirth","1994-03-12","requestedRole","PATIENT","hospitalId",1);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
   var account=users.findByUsername(name).orElseThrow();
   assertThat(jdbc.queryForObject("select status from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("PENDING");
   String token=jdbc.queryForObject("select confirmation_token from email_outbox where user_id=?",String.class,account.getId());

   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().isOk());

   assertThat(jdbc.queryForObject("select status from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("CANCELLED");
   assertThat(jdbc.queryForObject("select confirmation_token is null and recipient is null and first_name is null from email_outbox where user_id=?",Boolean.class,account.getId())).isTrue();
   dispatcher.dispatchDue();
   verify(email,never()).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
 }
 @Test void expiredAndMissingCsrfLinksCannotActivateAccount() throws Exception {
   String name=signup("PATIENT"),token=capturedToken();jdbc.update("update email_verifications set expires_at=now()-interval '1 minute' where user_id=?",users.findByUsername(name).orElseThrow().getId());
   String body=json.writeValueAsString(Map.of("token",token));
   mvc.perform(post("/api/v1/registration/verify").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
   assertThat(users.findByUsername(name).orElseThrow().isEnabled()).isFalse();
 }
 @Test void deliveryFailureKeepsAccountAndRetriesTheSameConfirmationToken() throws Exception {
   doThrow(new com.example.hospital.api.ApiException(503,"EMAIL_UNAVAILABLE","Delivery unavailable"))
       .doNothing().when(email).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
   String name="failure_"+UUID.randomUUID().toString().substring(0,8);
   mvc.perform(post("/api/v1/registration/signup").with(csrf()).with(r->{r.setRemoteAddr(name);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.test","password","RegistrationPassword123!","firstName","Maya","lastName","Koleva","dateOfBirth","1994-03-12","requestedRole","PATIENT","hospitalId",1)))).andExpect(status().isOk());
   var account=users.findByUsername(name).orElseThrow();
   assertThat(account.isEnabled()).isFalse();
   verify(email,never()).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
   assertThat(jdbc.queryForObject("select status from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("PENDING");
   assertThat(jdbc.queryForObject("select attempts from email_outbox where user_id=?",Integer.class,account.getId())).isZero();

   dispatcher.dispatchDue();
   String retryToken=jdbc.queryForObject("select confirmation_token from email_outbox where user_id=?",String.class,account.getId());
   assertThat(jdbc.queryForObject("select status from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("PENDING");
   assertThat(jdbc.queryForObject("select attempts from email_outbox where user_id=?",Integer.class,account.getId())).isEqualTo(1);
   assertThat(jdbc.queryForObject("select last_error from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("EMAIL_UNAVAILABLE");

   jdbc.update("update email_outbox set next_attempt_at=now()-interval '1 second' where user_id=?",account.getId());
   dispatcher.dispatchDue();
   assertThat(jdbc.queryForObject("select status from email_outbox where user_id=?",String.class,account.getId())).isEqualTo("SENT");
   assertThat(jdbc.queryForObject("select confirmation_token is null and recipient is null from email_outbox where user_id=?",Boolean.class,account.getId())).isTrue();
   assertThat(users.findByUsername(name).orElseThrow().isEnabled()).isFalse();
   var sentTokens=ArgumentCaptor.forClass(String.class);
   verify(email,times(2)).send(anyString(),anyString(),sentTokens.capture(),anyBoolean(),anyInt());
   assertThat(sentTokens.getAllValues()).containsExactly(retryToken,retryToken);
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
   dispatcher.dispatchDue();
   Long patientId=users.findByUsername(name).orElseThrow().getPatientId();
   assertThat(jdbc.queryForObject("select department_id from patients where id=?", Long.class, patientId)).isEqualTo(departmentId);
 }
 @Test void expiredUnverifiedRegistrationCanChangeEmailAndVerifyAgain() throws Exception {
   String name="recover_"+UUID.randomUUID().toString().substring(0,8), oldEmail=name+"@mistyped.example.test", newEmail=name+"@correct.example.test";
   signup(name,oldEmail,"PATIENT");
   String oldToken=capturedToken();
   var before=users.findByUsername(name).orElseThrow();
   long patientId=before.getPatientId();
   jdbc.update("update email_verifications set expires_at=now()-interval '1 minute' where user_id=?",before.getId());
   clearInvocations(email);

   assertRecoveryMessage(name,"RegistrationPassword123!",newEmail,name);
   var newToken=ArgumentCaptor.forClass(String.class);
   verify(email).send(eq(newEmail),eq("Maya"),newToken.capture(),eq(false),eq(30));
   var recovered=users.findByUsername(name).orElseThrow();
   assertThat(recovered.getEmail()).isEqualTo(newEmail);
   assertThat(recovered.isEnabled()).isFalse();
   assertThat(recovered.isEmailVerified()).isFalse();
   assertThat(recovered.getPatientId()).isEqualTo(patientId);
   assertThat(jdbc.queryForObject("select count(*) from email_verifications where user_id=?",Integer.class,recovered.getId())).isEqualTo(1);
   assertThat(jdbc.queryForObject("select token_hash from email_verifications where user_id=?",String.class,recovered.getId())).doesNotContain(newToken.getValue());

   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",oldToken)))).andExpect(status().isBadRequest());
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",newToken.getValue())))).andExpect(status().isOk());
   var verified=users.findByUsername(name).orElseThrow();
   assertThat(verified.getEmail()).isEqualTo(newEmail);
   assertThat(verified.isEmailVerified()).isTrue();
   assertThat(verified.isEnabled()).isTrue();
 }
 @Test void recoveryUsesOneGenericResponseForUnknownWrongPasswordAndActiveRegistrations() throws Exception {
   String name="active_"+UUID.randomUUID().toString().substring(0,8), oldEmail=name+"@example.test";
   signup(name,oldEmail,"PATIENT");
   capturedToken();
   clearInvocations(email);

   assertRecoveryMessage("missing_"+UUID.randomUUID().toString().substring(0,8),"RegistrationPassword123!","unknown@example.test","198.51.100.11");
   assertRecoveryMessage(name,"DifferentPassword123!",name+"@wrong-password.test","198.51.100.12");
   assertRecoveryMessage(name,"RegistrationPassword123!",name+"@active-link.test","198.51.100.13");
   var unchanged=users.findByUsername(name).orElseThrow();
   assertThat(unchanged.getEmail()).isEqualTo(oldEmail);
   assertThat(unchanged.isEmailVerified()).isFalse();
   assertThat(unchanged.isEnabled()).isFalse();
   verify(email,never()).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
 }
 @Test void recoveryCannotChangeVerifiedAccountOrUseAnEmailAlreadyClaimed() throws Exception {
   String verifiedName="verified_"+UUID.randomUUID().toString().substring(0,8), verifiedEmail=verifiedName+"@example.test";
   signup(verifiedName,verifiedEmail,"PATIENT");
   String verifiedToken=capturedToken();
   mvc.perform(post("/api/v1/registration/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",verifiedToken)))).andExpect(status().isOk());

   String targetName="target_"+UUID.randomUUID().toString().substring(0,8), targetEmail=targetName+"@mistyped.example.test";
   String ownerName="owner_"+UUID.randomUUID().toString().substring(0,8), ownedEmail=ownerName+"@example.test";
   signup(targetName,targetEmail,"PATIENT");
   signup(ownerName,ownedEmail,"PATIENT");
   var target=users.findByUsername(targetName).orElseThrow();
   jdbc.update("update email_verifications set expires_at=now()-interval '1 minute' where user_id=?",target.getId());
   clearInvocations(email);

   assertRecoveryMessage(verifiedName,"RegistrationPassword123!",verifiedName+"@replacement.test","198.51.100.14");
   assertRecoveryMessage(targetName,"RegistrationPassword123!",ownedEmail,"198.51.100.15");
   assertThat(users.findByUsername(verifiedName).orElseThrow().getEmail()).isEqualTo(verifiedEmail);
   assertThat(users.findByUsername(verifiedName).orElseThrow().isEmailVerified()).isTrue();
   assertThat(users.findByUsername(targetName).orElseThrow().getEmail()).isEqualTo(targetEmail);
   verify(email,never()).send(anyString(),anyString(),anyString(),anyBoolean(),anyInt());
 }
 @Test void recoveryRequiresCsrfAndRateLimitsByUsernameAcrossEmailChanges() throws Exception {
   var body=Map.of("username","rate_recovery_user","password","RegistrationPassword123!","email","first@example.test");
   mvc.perform(post("/api/v1/registration/recover").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
   assertRecoveryMessage("rate_recovery_user","RegistrationPassword123!","first@example.test","10.0.0.16");
   recover("rate_recovery_user","RegistrationPassword123!","second@example.test","10.0.0.16","203.0.113.45").andExpect(status().isTooManyRequests());
 }
}
