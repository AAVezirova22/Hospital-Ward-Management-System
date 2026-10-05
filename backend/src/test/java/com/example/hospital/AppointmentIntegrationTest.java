package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AppointmentIntegrationTest extends HospitalSupport {
  private Instant future() { return Instant.now().plusSeconds(40 * 86400L).truncatedTo(ChronoUnit.MINUTES); }

  private JsonNode doctor() throws Exception { return doctor(null); }
  private JsonNode doctor(Long departmentId) throws Exception {
    return result(request("admin", "POST", "/api/v1/doctors", Map.of(
        "doctorIdentifier", "APT-" + unique(), "firstName", "Elena", "lastName", "Dimitrova",
        "specialty", "Internal medicine", "active", true), departmentId), 201);
  }

  private Map<String, Object> booking(long doctorId, Instant start) {
    return Map.of("doctorId", doctorId, "attendeeName", "Aleksandar Kolev",
        "startsAt", start.toString(), "durationMinutes", 30, "contact", "+359000000000", "notes", "Synthetic appointment test");
  }

  private String availability(long doctorId, Instant start, int duration) {
    return "/api/v1/doctors/" + doctorId + "/availability?startsAt=" + start + "&durationMinutes=" + duration;
  }

  @Test
  void booksListsRejectsAllOverlapsAndCancellationReleasesTheWindow() throws Exception {
    var doctor = doctor();
    long id = doctor.path("id").asLong();
    var start = future();
    var before = result(request("staff", "GET", availability(id, start, 30), null), 200);
    assertThat(before.path("available").asBoolean()).isTrue();
    var booked = result(request("staff", "POST", "/api/v1/appointments", booking(id, start)), 201);
    assertThat(booked.path("attendeeName").asText()).isEqualTo("Aleksandar Kolev");
    assertThat(booked.path("status").asText()).isEqualTo("SCHEDULED");
    var directory = result(request("staff", "GET", "/api/v1/appointments?doctorId=" + id, null), 200);
    assertThat(directory.path("appointments").path("totalElements").asInt()).isEqualTo(1);
    var busy = result(request("staff", "GET", availability(id, start, 30), null), 200);
    assertThat(busy.path("available").asBoolean()).isFalse();
    assertThat(busy.toString()).doesNotContain("Aleksandar", "+359000000000");
    // Equal, partially overlapping on either side, and enclosing windows all conflict.
    for (var candidate : List.of(start, start.plusSeconds(900), start.minusSeconds(900))) {
      request("staff", "POST", "/api/v1/appointments", booking(id, candidate))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
    }
    var enclosing = new HashMap<>(booking(id, start.minusSeconds(600)));
    enclosing.put("durationMinutes", 60);
    request("staff", "POST", "/api/v1/appointments", enclosing).andExpect(status().isConflict());
    result(request("staff", "POST", "/api/v1/appointments", booking(id, start.plusSeconds(1800))), 201);
    request("staff", "POST", "/api/v1/appointments/" + booked.path("id").asLong() + "/cancel",
        Map.of("version", 99)).andExpect(status().isConflict());
    result(request("staff", "POST", "/api/v1/appointments/" + booked.path("id").asLong() + "/cancel",
        Map.of("version", booked.path("version").asLong())), 200);
    assertThat(result(request("staff", "GET", availability(id, start, 30), null), 200).path("available").asBoolean()).isTrue();
    result(request("staff", "POST", "/api/v1/appointments", booking(id, start)), 201);
    var cancelled = result(request("staff", "GET", "/api/v1/appointments?doctorId=" + id + "&status=CANCELLED", null), 200);
    assertThat(cancelled.path("appointments").path("totalElements").asInt()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type='APPOINTMENT_BOOKED' and entity_id=?",
        Long.class, booked.path("id").asLong())).isEqualTo(1L);
  }

  @Test
  void keyedBookingRetriesReturnTheSameReservation() throws Exception {
    long id = doctor().path("id").asLong();
    var body = booking(id, future());
    String key = "appointment-" + unique();
    var first = mvc.perform(post("/api/v1/appointments").with(user("staff")).with(csrf())
        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    var replay = mvc.perform(post("/api/v1/appointments").with(user("staff")).with(csrf())
        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true"))
        .andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(replay).path("id").asLong()).isEqualTo(json.readTree(first).path("id").asLong());
    assertThat(jdbc.queryForObject("select count(*) from doctor_appointments where doctor_id=?", Long.class, id)).isEqualTo(1L);
  }

  @Test
  void simultaneousRequestsReserveAWindowOnlyOnce() throws Exception {
    long id = doctor().path("id").asLong();
    var input = booking(id, future());
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> task = () -> {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return request("staff", "POST", "/api/v1/appointments", input).andReturn().getResponse().getStatus();
      };
      var one = executor.submit(task);
      var two = executor.submit(task);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(jdbc.queryForObject("select count(*) from doctor_appointments where doctor_id=?", Long.class, id)).isEqualTo(1L);
  }

  @Test
  void validatesPastTimeDurationNamesAndDoctorDeactivation() throws Exception {
    var doctor = doctor();
    long id = doctor.path("id").asLong();
    request("staff", "POST", "/api/v1/appointments", booking(id, Instant.now().minusSeconds(60)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPOINTMENT_IN_PAST"));
    for (var value : List.of(0, 4, 241)) {
      var input = new HashMap<>(booking(id, future())); input.put("durationMinutes", value);
      request("staff", "POST", "/api/v1/appointments", input).andExpect(status().isBadRequest());
    }
    var input = new HashMap<>(booking(id, future())); input.put("attendeeName", "   ");
    request("staff", "POST", "/api/v1/appointments", input).andExpect(status().isBadRequest());
    input.put("attendeeName", "Aleksandar Kolev"); input.put("startsAt", "November 2 16:30");
    request("staff", "POST", "/api/v1/appointments", input).andExpect(status().isBadRequest());
    result(request("staff", "POST", "/api/v1/appointments", booking(id, future())), 201);
    request("admin", "PUT", "/api/v1/doctors/" + id, Map.of(
        "doctorIdentifier", doctor.path("doctorIdentifier").asText(), "firstName", "Elena", "lastName", "Dimitrova",
        "specialty", "Internal medicine", "active", false, "version", doctor.path("version").asLong()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCTOR_HAS_APPOINTMENTS"));
    var inactive = doctor();
    long inactiveId = inactive.path("id").asLong();
    result(request("admin", "PUT", "/api/v1/doctors/" + inactiveId, Map.of(
        "doctorIdentifier", inactive.path("doctorIdentifier").asText(), "firstName", "Elena", "lastName", "Dimitrova",
        "specialty", "Internal medicine", "active", false, "version", inactive.path("version").asLong())), 200);
    request("staff", "POST", "/api/v1/appointments", booking(inactiveId, future()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCTOR_INACTIVE"));
  }

  @Test
  void doctorReadsOnlyOwnScheduleAndPatientsCannotBookOrReadStaffSchedules() throws Exception {
    long other = doctor().path("id").asLong();
    var mine = result(request("staff", "POST", "/api/v1/appointments", booking(1, future().plusSeconds(86400))), 201);
    result(request("staff", "POST", "/api/v1/appointments", booking(other, future())), 201);
    var own = result(request("doctor", "GET", "/api/v1/appointments", null), 200);
    assertThat(own.path("appointments").path("items")).allSatisfy(a -> assertThat(a.path("doctor").path("id").asLong()).isEqualTo(1));
    request("doctor", "GET", "/api/v1/appointments?doctorId=" + other, null).andExpect(status().isForbidden());
    request("doctor", "GET", availability(other, future(), 30), null).andExpect(status().isForbidden());
    request("doctor", "POST", "/api/v1/appointments", booking(1, future())).andExpect(status().isForbidden());
    request("doctor", "POST", "/api/v1/appointments/" + mine.path("id").asLong() + "/cancel", Map.of("version", 0))
        .andExpect(status().isForbidden());
    var patient = new com.example.hospital.domain.AppUser();
    patient.setUsername("appointment-patient-" + unique());
    patient.setPasswordHash(users.findByUsername("admin").orElseThrow().getPasswordHash());
    patient.setRole("PATIENT");
    patient.setPatientId(createPatient().path("id").asLong());
    users.saveAndFlush(patient);
    request(patient.getUsername(), "GET", "/api/v1/appointments", null).andExpect(status().isForbidden());
    request(patient.getUsername(), "POST", "/api/v1/appointments", booking(other, future())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/appointments")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/appointments").with(user("staff")).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(booking(other, future())))).andExpect(status().isForbidden());
  }

  @Test
  void appointmentsStayInTheDoctorsDepartment() throws Exception {
    var workspace = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Appointment hospital " + unique(), "departmentName", "Clinic")), 201);
    long department = workspace.path("departmentId").asLong();
    long foreign = doctor(department).path("id").asLong();
    request("admin", "POST", "/api/v1/appointments", booking(foreign, future()))
        .andExpect(status().isNotFound());
    var saved = result(request("admin", "POST", "/api/v1/appointments", booking(foreign, future()), department), 201);
    request("admin", "POST", "/api/v1/appointments/" + saved.path("id").asLong() + "/cancel", Map.of("version", 0))
        .andExpect(status().isNotFound());
    request("admin", "GET", availability(foreign, future(), 30), null).andExpect(status().isNotFound());
    var hidden = result(request("admin", "GET", "/api/v1/appointments?q=Aleksandar", null), 200);
    assertThat(hidden.path("appointments").path("items")).noneSatisfy(a -> assertThat(a.path("id").asLong()).isEqualTo(saved.path("id").asLong()));
  }

  @Test
  void departmentTimezoneAndDstAreAppliedOnTheServer() throws Exception {
    var workspace = result(request("admin", "POST", "/api/v1/workspaces/hospitals",
        Map.of("name", "Timezone appointments " + unique(), "departmentName", "Sofia clinic")), 201);
    long department = workspace.path("departmentId").asLong();
    jdbc.update("update departments set time_zone='Europe/Sofia' where id=?", department);
    long id = doctor(department).path("id").asLong();
    int year = LocalDate.now().getYear() + 2;
    String local = year + "-11-02T16:30";
    var booking = new HashMap<>(booking(id, future())); booking.put("startsAt", local);
    var saved = result(request("admin", "POST", "/api/v1/appointments", booking, department), 201);
    assertThat(saved.path("startsAt").asText()).isEqualTo(year + "-11-02T14:30:00Z");
    assertThat(saved.path("timeZone").asText()).isEqualTo("Europe/Sofia");
    var filter = result(request("admin", "GET", "/api/v1/appointments?from=" + year + "-11-02&to=" + year + "-11-02", null, department), 200);
    assertThat(filter.path("appointments").path("totalElements").asInt()).isEqualTo(1);
    var autumn = LocalDate.of(year, 10, 31).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    booking.put("startsAt", autumn + "T03:30");
    request("admin", "POST", "/api/v1/appointments", booking, department)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMBIGUOUS_APPOINTMENT_TIME"));
    var spring = LocalDate.of(year, 3, 31).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    booking.put("startsAt", spring + "T03:30");
    request("admin", "POST", "/api/v1/appointments", booking, department)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AMBIGUOUS_APPOINTMENT_TIME"));
  }

  @Test
  void assistantPreparesWithoutBookingAndConfirmsOnceWithOwnerAndConflictChecks() throws Exception {
    var doctor = doctor();
    var start = future();
    String command = "Book appointment with " + doctor.path("doctorIdentifier").asText() + " on " + start + " for Aleksandar Kolev";
    var proposal = ai("staff", command, null);
    assertThat(proposal.path("responseType").asText()).isEqualTo("CONFIRMATION_CARD");
    assertThat(proposal.path("data").path("appointment").path("attendeeName").asText()).isEqualTo("Aleksandar Kolev");
    long action = proposal.path("data").path("action").path("id").asLong();
    long doctorId = doctor.path("id").asLong();
    assertThat(jdbc.queryForObject("select count(*) from doctor_appointments where doctor_id=?", Long.class, doctorId)).isZero();
    request("admin", "POST", "/api/v1/ai-actions/" + action + "/confirm", null).andExpect(status().isForbidden());
    var saved = result(request("staff", "POST", "/api/v1/ai-actions/" + action + "/confirm", null), 200);
    assertThat(saved.path("status").asText()).isEqualTo("SCHEDULED");
    request("staff", "POST", "/api/v1/ai-actions/" + action + "/confirm", null)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTION_CONSUMED"));
    var next = start.plusSeconds(3600);
    proposal = ai("staff", command.replace(start.toString(), next.toString()), null);
    action = proposal.path("data").path("action").path("id").asLong();
    result(request("admin", "POST", "/api/v1/appointments", booking(doctorId, next)), 201);
    request("staff", "POST", "/api/v1/ai-actions/" + action + "/confirm", null)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
    assertThat(jdbc.queryForObject("select status from ai_pending_actions where id=?", String.class, action)).isEqualTo("PENDING");
    proposal = ai("staff", command.replace(start.toString(), start.plusSeconds(7200).toString()), null);
    action = proposal.path("data").path("action").path("id").asLong();
    jdbc.update("update ai_pending_actions set expires_at=now()-interval '1 minute' where id=?", action);
    request("staff", "POST", "/api/v1/ai-actions/" + action + "/confirm", null)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTION_EXPIRED"));
  }
}
