package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiToolRegistry {
  private final HospitalService h;
  private final AiActionService actions;
  private final Actor actor;

  public AiToolRegistry(HospitalService h, AiActionService a, Actor actor) {
    this.h = h;
    actions = a;
    this.actor = actor;
  }

  public record Response(
      String responseType, String message, Object data, String sessionId, String model) {}

  public static final Map<String, List<String>> SCHEMAS =
      Map.ofEntries(
          Map.entry("searchPatients", List.of("query")),
          Map.entry("getPatientSummary", List.of("patientQuery")),
          Map.entry("getAvailableRooms", List.of("minimumFreeBeds")),
          Map.entry("getRoomOccupancy", List.of("minimumFreeBeds")),
          Map.entry("getDoctorPatients", List.of("doctorQuery")),
          Map.entry("getAdmission", List.of("admissionId")),
          Map.entry("getAdmissions", List.of("from", "to")),
          Map.entry("getProcedureStatistics", List.of("from", "to")),
          Map.entry("getDashboardSummary", List.of()),
          Map.entry("prepareAdmission", List.of("patientQuery", "doctorQuery", "roomNumber")),
          Map.entry("prepareTransfer", List.of("patientQuery", "roomNumber")),
          Map.entry("prepareDischarge", List.of("patientQuery")),
          Map.entry("navigate", List.of("route")),
          Map.entry("help", List.of()));

  public List<Map<String, Object>> definitions() {
    return SCHEMAS.entrySet().stream()
        .filter(e -> !actor.doctor() || !e.getKey().startsWith("prepare"))
        .map(
            e -> {
              Map<String, Object> props = new LinkedHashMap<>();
              e.getValue().forEach(k -> props.put(k, Map.of("type", "string", "maxLength", 500)));
              return Map.<String, Object>of(
                  "type",
                  "function",
                  "function",
                  Map.of(
                      "name",
                      e.getKey(),
                      "description",
                      e.getKey().startsWith("prepare")
                          ? "Prepare for explicit human confirmation; never executes a write"
                          : "Authorized operational query or navigation",
                      "parameters",
                      Map.of(
                          "type", "object", "properties", props, "additionalProperties", false)));
            })
        .toList();
  }

  private Response response(String type, String message, Object data) {
    return new Response(type, message, data, null, null);
  }

  private Patient resolve(String q, Long selected) {
    if ((q == null
            || q.isBlank()
            || List.of("him", "her", "patient", "his", "her current summary")
                .contains(q.toLowerCase()))
        && selected != null) return h.patient(selected);
    var matches = h.patients(q == null ? "" : q);
    if (matches.size() != 1)
      throw new ApiException(
          400,
          "PATIENT_AMBIGUOUS",
          "Select a patient dossier or provide an unambiguous patient name or identifier.");
    return matches.get(0);
  }

  private Doctor doctor(String q) {
    var ds =
        h.doctors().stream()
            .filter(
                d ->
                    d.active
                        && (d.firstName + " " + d.lastName + " " + d.doctorIdentifier)
                            .toLowerCase()
                            .contains(q.toLowerCase()))
            .toList();
    if (ds.size() != 1)
      throw new ApiException(
          400, "DOCTOR_AMBIGUOUS", "Specify one active doctor by name or identifier.");
    return ds.get(0);
  }

  public Response execute(AiModelClient.ToolCall call, Long selected) {
    var keys = SCHEMAS.get(call.name());
    if (keys == null
        || call.arguments() == null
        || !keys.containsAll(call.arguments().keySet())
        || call.arguments().values().stream().anyMatch(v -> v == null || v.length() > 500))
      throw new ApiException(
          400, "INVALID_TOOL_CALL", "The assistant returned an invalid tool request.");
    if (actor.doctor() && call.name().startsWith("prepare"))
      throw new AccessDeniedException("Doctors cannot prepare admission changes");
    var a = call.arguments();
    return switch (call.name()) {
      case "help" ->
          response(
              "TEXT",
              "I can find patients, show room capacity, summarize records, report procedures and"
                  + " prepare admissions, transfers or discharges. I cannot make clinical decisions"
                  + " or change permissions.",
              Map.of());
      case "searchPatients" ->
          response(
              "PATIENT_LIST",
              "Matching patients within your access.",
              Map.of("patients", h.patients(a.getOrDefault("query", ""))));
      case "getPatientSummary" ->
          response(
              "PATIENT_SUMMARY",
              "Recorded operational history.",
              h.summary(resolve(a.get("patientQuery"), selected).id));
      case "getAvailableRooms", "getRoomOccupancy" -> {
        int n = Integer.parseInt(a.getOrDefault("minimumFreeBeds", "0"));
        if (n < 0 || n > 100) throw new IllegalArgumentException();
        yield response(
            "ROOM_LIST",
            "Current capacity; availability is checked again before placement.",
            Map.of("rooms", h.rooms(n)));
      }
      case "getDoctorPatients" ->
          response(
              "REPORT_RESULT",
              "Current assigned patients.",
              Map.of("admissions", h.census(null, doctor(a.getOrDefault("doctorQuery", "")).id)));
      case "getAdmission" ->
          response(
              "REPORT_RESULT",
              "Admission record.",
              h.admissionView(h.admission(Long.valueOf(a.get("admissionId")))));
      case "getAdmissions" -> {
        var from = LocalDate.parse(a.get("from"));
        var to = LocalDate.parse(a.get("to"));
        if (from.isAfter(to)) throw new IllegalArgumentException();
        yield response(
            "REPORT_RESULT",
            "Admissions for the requested period.",
            Map.of(
                "admissions",
                h.admissions().stream()
                    .filter(
                        ad ->
                            !ad.admissionDateTime.isBefore(
                                    from.atStartOfDay().toInstant(ZoneOffset.UTC))
                                && ad.admissionDateTime.isBefore(
                                    to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .map(h::admissionView)
                    .toList()));
      }
      case "getProcedureStatistics" ->
          response(
              "REPORT_RESULT",
              "Procedure totals calculated from saved records.",
              h.procedureReport(
                  LocalDate.parse(a.getOrDefault("from", LocalDate.now(ZoneOffset.UTC).toString())),
                  LocalDate.parse(a.getOrDefault("to", LocalDate.now(ZoneOffset.UTC).toString())),
                  null,
                  null));
      case "getDashboardSummary" ->
          response("REPORT_RESULT", "Current department operations.", h.dashboard());
      case "navigate" -> {
        var route = a.getOrDefault("route", "");
        if (!Set.of(
                    "/app/dashboard",
                    "/app/patients",
                    "/app/rooms",
                    "/app/admissions",
                    "/app/doctors",
                    "/app/reports",
                    "/app/procedures",
                    "/app/users")
                .contains(route)
            || !actor.user().role.equals("ADMIN") && route.equals("/app/users"))
          throw new AccessDeniedException("Route not available");
        yield response("NAVIGATION_COMMAND", "Open requested view.", Map.of("route", route));
      }
      case "prepareAdmission", "prepareTransfer", "prepareDischarge" -> {
        var p = resolve(a.get("patientQuery"), selected);
        Long roomId = null, doctorId = null, admissionId = null, version = null;
        String type = call.name().substring(7).toUpperCase();
        if (!type.equals("DISCHARGE")) {
          String number = a.getOrDefault("roomNumber", "");
          if (number.isBlank())
            yield response(
                "NAVIGATION_COMMAND",
                "Select the room and doctor in the standard admission or transfer form.",
                Map.of("route", "/app/patients/" + p.id));
          var rm =
              h.rooms(1).stream()
                  .filter(
                      r ->
                          r.get("roomNumber").equals(number)
                              && Boolean.TRUE.equals(r.get("active")))
                  .toList();
          if (rm.size() != 1)
            throw ApiException.conflict(
                "ROOM_CAPACITY_EXCEEDED", "That room has no available capacity.");
          roomId = ((Number) rm.get(0).get("id")).longValue();
        }
        if (type.equals("ADMISSION")) {
          if (a.getOrDefault("doctorQuery", "").isBlank())
            yield response(
                "NAVIGATION_COMMAND",
                "Select an attending doctor in the admission form.",
                Map.of("route", "/app/patients/" + p.id));
          doctorId = doctor(a.get("doctorQuery")).id;
          if (h.admissions().stream()
              .anyMatch(ad -> ad.patientId.equals(p.id) && ad.status.equals("ACTIVE")))
            throw ApiException.conflict("ALREADY_ADMITTED", "Patient already admitted.");
        } else {
          var active =
              h.admissions().stream()
                  .filter(ad -> ad.patientId.equals(p.id) && ad.status.equals("ACTIVE"))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new ApiException(
                              400, "NO_ACTIVE_ADMISSION", "No active admission for this patient."));
          admissionId = active.id;
          version = active.version;
        }
        yield response(
            "CONFIRMATION_CARD",
            "Review this proposal. No hospital records have changed.",
            actions.prepare(
                type,
                new AiActionService.Payload(
                    p.id, admissionId, roomId, doctorId, version, "User-requested AI transfer")));
      }
      default -> throw new IllegalArgumentException();
    };
  }
}
