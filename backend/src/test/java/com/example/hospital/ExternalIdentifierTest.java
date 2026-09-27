package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalIdentifierTest extends HospitalSupport {
  private Map<String, Object> link(long patientId, String namespace, String value) {
    var body = new HashMap<String, Object>();
    body.put("entityType", "PATIENT");
    body.put("entityId", patientId);
    body.put("namespace", namespace);
    body.put("value", value);
    body.put("source", "admissions-import");
    return body;
  }

  @Test
  void identifiersResolveToOneRecordAndConflictsAreRefused() throws Exception {
    long first = createPatient().get("id").asLong();
    long second = createPatient().get("id").asLong();
    String namespace = "urn:mrn:city-" + unique();
    String mrn = "MRN-" + unique();

    var linked = result(request("staff", "POST", "/api/v1/external-ids", link(first, namespace, mrn)), 201);
    var again = result(request("staff", "POST", "/api/v1/external-ids", link(first, namespace, mrn)), 201);
    assertThat(again.get("id").asLong()).as("identical link is idempotent").isEqualTo(linked.get("id").asLong());

    var resolved = result(request("doctor", "GET",
        "/api/v1/external-ids/resolve?entityType=PATIENT&namespace=" + namespace + "&value=" + mrn, null), 200);
    assertThat(resolved.get("entity_id").asLong()).isEqualTo(first);
    assertThat(result(request("staff", "GET", "/api/v1/external-ids?entityType=PATIENT&entityId=" + first, null), 200)
            .get(0).get("source").asText())
        .isEqualTo("admissions-import");

    request("staff", "POST", "/api/v1/external-ids", link(second, namespace, mrn))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EXTERNAL_ID_CONFLICT"));
    request("staff", "POST", "/api/v1/external-ids", link(first, namespace, "OTHER-" + unique()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EXTERNAL_ID_NAMESPACE_TAKEN"));
    result(request("staff", "POST", "/api/v1/external-ids", link(first, "urn:nhs:" + unique(), mrn)), 201);

    String metadata = jdbc.queryForObject(
        "select metadata from audit_events where event_type = 'EXTERNAL_ID_LINKED' and entity_id = ? order by id desc limit 1",
        String.class, first);
    assertThat(metadata).contains("namespace=").doesNotContain(mrn);
  }

  @Test
  void onlyExistingRecordsCanBeLinkedAndOnlyAdminsUnlink() throws Exception {
    long patient = createPatient().get("id").asLong();
    request("staff", "POST", "/api/v1/external-ids", link(999999999L, "urn:x", "1"))
        .andExpect(status().isNotFound());
    request("staff", "POST", "/api/v1/external-ids", link(patient, "bad namespace!", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_NAMESPACE"));
    request("doctor", "POST", "/api/v1/external-ids", link(patient, "urn:x", "1"))
        .andExpect(status().isForbidden());

    String namespace = "urn:lab:" + unique();
    long id = result(request("staff", "POST", "/api/v1/external-ids", link(patient, namespace, "L-1")), 201)
        .get("id").asLong();
    request("staff", "DELETE", "/api/v1/external-ids/" + id, null).andExpect(status().isForbidden());
    request("admin", "DELETE", "/api/v1/external-ids/" + id, null).andExpect(status().isNoContent());
    request("admin", "GET", "/api/v1/external-ids/resolve?entityType=PATIENT&namespace=" + namespace + "&value=L-1", null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXTERNAL_ID_NOT_FOUND"));
  }
}
