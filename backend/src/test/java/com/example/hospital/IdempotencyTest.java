package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class IdempotencyTest extends HospitalSupport {
  private ResultActions createPatient(String who, String key, String identifier) throws Exception {
    var body = Map.of("patientIdentifier", identifier, "firstName", "Retry", "lastName", "Safe", "dateOfBirth", "1985-05-05");
    var builder = post("/api/v1/patients").with(user(who)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    if (key != null) builder.header("Idempotency-Key", key);
    return mvc.perform(builder);
  }

  private long patients(String identifier) {
    return jdbc.queryForObject("select count(*) from patients where patient_identifier = ?", Long.class, identifier);
  }

  @Test
  void retriesWithTheSameKeyReplayTheFirstResponseOnce() throws Exception {
    String key = "create-" + unique();
    String identifier = "IDEM-" + unique();
    String first = createPatient("admin", key, identifier)
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist("Idempotent-Replayed"))
        .andReturn().getResponse().getContentAsString();
    String second = createPatient("admin", key, identifier)
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"))
        .andReturn().getResponse().getContentAsString();

    assertThat(json.readTree(second).get("id").asLong()).isEqualTo(json.readTree(first).get("id").asLong());
    assertThat(patients(identifier)).isOne();
  }

  @Test
  void aKeyCannotBeReusedForADifferentRequestAndMustBeWellFormed() throws Exception {
    String key = "reuse-" + unique();
    createPatient("admin", key, "IDEM-" + unique()).andExpect(status().isCreated());
    createPatient("admin", key, "IDEM-" + unique())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    createPatient("admin", "has spaces", "IDEM-" + unique())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
  }

  @Test
  void keysBelongToOneAccountAndClientErrorsAreReplayedToo() throws Exception {
    String key = "shared-" + unique();
    String identifier = "IDEM-" + unique();
    createPatient("admin", key, identifier).andExpect(status().isCreated());
    createPatient("staff", key, identifier)
        .andExpect(status().isConflict())
        .andExpect(header().doesNotExist("Idempotent-Replayed"))
        .andExpect(jsonPath("$.code").value("PATIENT_IDENTIFIER_TAKEN"));
    createPatient("staff", key, identifier)
        .andExpect(status().isConflict())
        .andExpect(header().string("Idempotent-Replayed", "true"));
    assertThat(patients(identifier)).isOne();
  }

  @Test
  void requestsWithoutAKeyBehaveAsBefore() throws Exception {
    String identifier = "IDEM-" + unique();
    createPatient("admin", null, identifier).andExpect(status().isCreated());
    createPatient("admin", null, identifier).andExpect(status().isConflict());
  }
}
