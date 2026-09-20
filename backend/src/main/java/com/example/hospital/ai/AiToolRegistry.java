package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiToolRegistry {
  private final HospitalService h;
  private final AiActionService actions;
  private final Actor actor;
  private final WorkspaceService workspaces;

  public AiToolRegistry(HospitalService h, AiActionService a, Actor actor, WorkspaceService workspaces) {
    this.h = h;
    actions = a;
    this.actor = actor;
    this.workspaces = workspaces;
  }

  public record Response(
      String responseType, String message, Object data, String sessionId, String model) {}

  public static final Map<String, List<String>> SCHEMAS = AiToolSchemas.SCHEMAS;

  public List<Map<String, Object>> agentDefinitions() {
    var definitions = new ArrayList<>(definitions());
    definitions.add(definition("respond", "Answer the user or ask for missing fields. Never claim changes before confirmation.", "message", 4000));
    definitions.add(definition("readConnectedFiles", "Read files from the user-connected folder. ids is a comma-separated list of manifest IDs, at most 10. Never invent IDs or paths.", "ids", 700));
    definitions.add(definition("getWorkflowCatalogue", "Read authorized doctors, rooms, procedures and workspace names for workflow planning.", null, 0));
    if (!actor.doctor())
      definitions.add(definition("prepareWorkflow", AiWorkflowService.DESCRIPTION, "plan", 50000));
    return definitions;
  }

  private Map<String, Object> definition(String name, String description, String argument, int length) {
    return Map.of("type", "function", "function", Map.of("name", name, "description", description,
        "parameters", Map.of("type", "object", "properties", argument == null ? Map.of()
            : Map.of(argument, Map.of("type", "string", "maxLength", length)),
            "required", argument == null ? List.of() : List.of(argument), "additionalProperties", false)));
  }

  public Response executeAgent(AiModelClient.ToolCall call, Long selected) {
    if (call.name().equals("prepareWorkflow")) {
      requireArgument(call, "plan", 50000);
      return response("WORKFLOW_PROPOSAL", "Review every step and its source. Confirm to apply the complete workflow.", actions.prepareWorkflow(call.arguments().get("plan")));
    }
    if (call.name().equals("respond")) {
      requireArgument(call, "message", 4000);
      return response("TEXT", call.arguments().get("message"), Map.of());
    }
    if (call.name().equals("getWorkflowCatalogue")) {
      if (call.arguments() == null || !call.arguments().isEmpty()) throw new IllegalArgumentException();
      return response("REPORT_RESULT", "Current authorized catalogue.",
          Map.of("doctors", h.doctors(), "rooms", h.rooms(0), "procedures", h.procedures()));
    }
    return execute(call, selected);
  }

  public static void requireArgument(AiModelClient.ToolCall call, String name, int max) {
    if (call.arguments() == null || !call.arguments().keySet().equals(Set.of(name))
        || call.arguments().get(name) == null || call.arguments().get(name).isBlank()
        || call.arguments().get(name).length() > max) throw new IllegalArgumentException();
  }

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
    try {
      return switch (call.name()) {
      case "help" ->
          response(
              "TEXT",
              "I can find patients, show room capacity, summarize records, report procedures, list"
                  + " your hospitals and prepare admissions, transfers or discharges. Tools stay"
                  + " inside the open department unless you ask for listWorkspaces. I cannot make"
                  + " clinical decisions or change permissions.",
              Map.of());
      case "searchPatients" ->
          response(
              "PATIENT_LIST",
              "Matching patients within your access.",
              Map.of("department", h.scopeLabel(), "patients", h.patients(a.getOrDefault("query", "")).stream().map(Views.PatientDirectory::of).toList()));
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
      case "listWorkspaces" ->
          response(
              "REPORT_RESULT",
              "Hospitals and departments you can open. Clinical tools stay in the current department.",
              Map.of(
                  "activeDepartmentId",
                  com.example.hospital.security.DepartmentContext.id(),
                  "hospitals",
                  workspaces.list().stream()
                      .map(
                          hospital ->
                              Map.of(
                                  "id",
                                  hospital.id(),
                                  "name",
                                  hospital.name(),
                                  "departments",
                                  hospital.departments().stream()
                                      .map(
                                          department ->
                                              Map.of(
                                                  "id",
                                                  department.id(),
                                                  "name",
                                                  department.name(),
                                                  "role",
                                                  department.role()))
                                      .toList()))
                      .toList()));
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
                    "/app/users",
                    "/app/planner",
                    "/app/audit",
                    "/app/presentation")
                .contains(route)
            && !route.matches("^/app/patients/.+")
            || !actor.user().role.equals("ADMIN") && (route.equals("/app/users") || route.equals("/app/audit")))
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
                Map.of("route", "/app/patients/" + p.patientIdentifier));
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
                Map.of("route", "/app/patients/" + p.patientIdentifier));
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
    } catch (NumberFormatException | DateTimeParseException | IllegalArgumentException e) {
      throw new ApiException(
          400, "INVALID_TOOL_CALL", "The assistant returned an invalid tool request.");
    }
  }
}
