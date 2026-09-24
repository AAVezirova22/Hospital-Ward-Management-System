package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class CalendarFeedTest extends HospitalSupport {
  private String feedPath(String who) throws Exception {
    String url = result(request(who, "POST", "/api/v1/calendar/feed", null), 201).get("url").asText();
    return url.substring(url.indexOf("/api/v1/"));
  }

  private String fetch(String path) throws Exception {
    return mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/calendar"))
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  void feedListsExpectedDischargesWithoutPatientDetails() throws Exception {
    var patient = createPatient();
    long admission = admit(patient, room(1)).get("id").asLong();
    LocalDate day = LocalDate.now().plusDays(3);
    jdbc.update("update admissions set expected_discharge_date = ? where id = ?", java.sql.Date.valueOf(day), admission);

    String ics = fetch(feedPath("staff"));
    assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").contains(
        "UID:admission-" + admission + "-expected-discharge@medcore",
        "DTSTART;VALUE=DATE:" + day.format(DateTimeFormatter.BASIC_ISO_DATE),
        "DESCRIPTION:Admission #" + admission);
    assertThat(ics)
        .doesNotContain(patient.get("firstName").asText())
        .doesNotContain(patient.get("patientIdentifier").asText());
  }

  @Test
  void doctorsOnlySeeTheirOwnPatients() throws Exception {
    long admission = admit(createPatient(), room(1)).get("id").asLong();
    long doctorId = users.findByUsername("doctor").orElseThrow().getDoctorId();
    long otherDoctor = jdbc.queryForObject(
        "select id from doctors where department_id = 1 and id <> ? and active order by id limit 1", Long.class, doctorId);
    jdbc.update("update admissions set expected_discharge_date = current_date + 1, attending_doctor_id = ? where id = ?",
        otherDoctor, admission);

    assertThat(fetch(feedPath("doctor"))).doesNotContain("admission-" + admission + "-");
    assertThat(fetch(feedPath("staff"))).contains("admission-" + admission + "-");
  }

  @Test
  void revokedReplacedAndUnknownFeedsStopWorking() throws Exception {
    String first = feedPath("admin");
    fetch(first);
    String second = feedPath("admin");
    mvc.perform(get(first)).andExpect(status().isNotFound());
    fetch(second);
    request("admin", "DELETE", "/api/v1/calendar/feed", null).andExpect(status().isNoContent());
    mvc.perform(get(second)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/calendar/feeds/not-a-real-token-at-all-000000.ics")).andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("select count(*) from calendar_feeds where token_hash like '%/%'", Long.class)).isZero();
  }
}
