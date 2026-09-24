package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class PaginationLinksTest extends HospitalSupport {
  private MockHttpServletResponse fetch(String path) throws Exception {
    return mvc.perform(get(path).with(user("admin"))).andExpect(status().isOk()).andReturn().getResponse();
  }

  @Test
  void pagedCollectionsCarryNavigationLinksThatKeepFilters() throws Exception {
    String prefix = "LINK-" + unique();
    for (int i = 0; i < 5; i++)
      result(request("admin", "POST", "/api/v1/rooms",
          java.util.Map.of("roomNumber", prefix + "-" + i, "bedCount", 1, "active", true)), 201);

    var middle = fetch("/api/v1/rooms?q=" + prefix + "&size=2&page=1");
    assertThat(middle.getHeader("X-Total-Count")).isEqualTo("5");
    String link = middle.getHeader("Link");
    assertThat(link)
        .contains("</api/v1/rooms?q=" + prefix + "&page=0&size=2>; rel=\"first\"")
        .contains("page=0&size=2>; rel=\"prev\"")
        .contains("page=2&size=2>; rel=\"next\"")
        .contains("page=2&size=2>; rel=\"last\"")
        .doesNotContain("http://");

    var last = fetch("/api/v1/rooms?q=" + prefix + "&size=2&page=2");
    assertThat(last.getHeader("Link")).doesNotContain("rel=\"next\"").contains("rel=\"prev\"");
    var first = fetch("/api/v1/rooms?q=" + prefix + "&size=2");
    assertThat(first.getHeader("Link")).doesNotContain("rel=\"prev\"");
  }

  @Test
  void patientAdmissionAndAuditListsShareTheSameContract() throws Exception {
    createPatient();
    for (String path : new String[] {"/api/v1/patients?size=1", "/api/v1/admissions?size=1", "/api/v1/audit?size=1"}) {
      var response = fetch(path);
      assertThat(response.getHeader("X-Total-Count")).as(path).isNotBlank();
      assertThat(response.getHeader("Link")).as(path).contains("rel=\"first\"", "rel=\"last\"");
    }
    var patients = json.readTree(fetch("/api/v1/patients?size=1").getContentAsString());
    assertThat(patients.has("items") && patients.has("totalPages") && patients.has("nextPage")).isTrue();
    var admissions = json.readTree(fetch("/api/v1/admissions?size=1").getContentAsString());
    assertThat(admissions.has("items") && admissions.has("totalElements") && admissions.has("hasNext")).isTrue();
  }

  @Test
  void emptyCollectionsPointFirstAndLastAtPageZero() throws Exception {
    var empty = fetch("/api/v1/rooms?q=NO-SUCH-ROOM-" + unique());
    assertThat(empty.getHeader("X-Total-Count")).isEqualTo("0");
    assertThat(empty.getHeader("Link")).contains("page=0&size=20>; rel=\"last\"").doesNotContain("rel=\"next\"");
  }
}
