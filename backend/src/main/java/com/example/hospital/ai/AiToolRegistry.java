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
  private final ReportService reports;
  private final AiActionService actions;
  private final Actor actor;
  private final WorkspaceService workspaces;
  private final DepartmentTimeService departmentTime;
  private final AuditService audit;

  public AiToolRegistry(
      HospitalService h, ReportService reports, AiActionService a, Actor actor, WorkspaceService workspaces,
      DepartmentTimeService departmentTime, AuditService audit) {
    this.h = h;
    this.reports = reports;
    actions = a;
    this.actor = actor;
    this.workspaces = workspaces;
    this.departmentTime = departmentTime;
    this.audit = audit;
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
    return executeAgent(call, selected, List.of(), List.of());
  }

  public Response executeAgent(AiModelClient.ToolCall call, Long selected, List<String> fileSourceNames) {
    return executeAgent(call, selected, fileSourceNames, List.of());
  }

  public Response executeAgent(AiModelClient.ToolCall call, Long selected, List<String> fileSourceNames,
      List<String> attachedSourceIds) {
    if (call.name().equals("prepareWorkflow")) {
      requireArgument(call, "plan", 50000);
      return response("WORKFLOW_PROPOSAL", "Review every step, field evidence and source. Resolve uncertain evidence, then confirm to apply the complete workflow.",
          actions.prepareWorkflow(call.arguments().get("plan"), fileSourceNames, attachedSourceIds));
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
              e.getValue().forEach(
                  k -> props.put(k, Map.of("type", "string", "maxLength", argumentLimit(k))));
              return Map.<String, Object>of(
                  "type",
                  "function",
                  "function",
                  Map.of(
                      "name",
                      e.getKey(),
                      "description",
                      toolDescription(e.getKey()),
                      "parameters",
                      Map.of(
                          "type", "object", "properties", props, "additionalProperties", false)));
            })
        .toList();
  }

  private Response response(String type, String message, Object data) {
    return new Response(type, message, data, null, null);
  }

  private static int argumentLimit(String name) {
    return name.toLowerCase(Locale.ROOT).contains("capabilities") ? 2000 : 500;
  }

  private static String toolDescription(String name) {
    return switch (name) {
      case "getAvailableRooms" ->
          "Find active rooms with free beds. requiredCapabilities is an optional comma-separated list of tags; incompatible or unavailable rooms are returned separately with reasons.";
      case "getRoomOccupancy" ->
          "Show room occupancy. requiredCapabilities is an optional comma-separated list of tags; rooms missing any requested tag are excluded with reasons.";
      case "prepareAdmission" ->
          "Prepare an admission proposal for review. requiredRoomCapabilities is an optional comma-separated list; choose only a room that supports every requested tag.";
      case "prepareTransfer" ->
          "Prepare a transfer for review. The admission's saved room requirements apply; choose only a compatible room with available capacity.";
      default -> "Read authorized hospital information or prepare a change for human review.";
    };
  }

  private static Collection<String> roomCapabilities(Map<String, Object> room) {
    Object capabilities = room.get("capabilities");
    if (!(capabilities instanceof Collection<?> values)) return List.of();
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  private Patient resolve(String q, Long selected) {
    if ((q == null
            || q.isBlank()
            || List.of("him", "her", "patient", "his", "her current summary")
                .contains(q.toLowerCase()))
        && selected != null) return h.patient(selected);
    var matches = h.patientMatches(q == null ? "" : q, 2);
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
                    d.isActive()
                        && (d.getFirstName() + " " + d.getLastName() + " " + d.getDoctorIdentifier())
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
        || call.arguments().entrySet().stream()
            .anyMatch(e -> e.getValue() == null || e.getValue().length() > argumentLimit(e.getKey())))
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
      case "searchPatients" -> {
        var page = h.patients(a.getOrDefault("query", ""), 0, 25);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("department", h.scopeLabel());
        data.put("patients", page.getContent().stream().map(Views.PatientDirectory::of).toList());
        data.put("totalElements", page.getTotalElements());
        data.put("hasNext", page.hasNext());
        data.put("nextPage", page.hasNext() ? page.getNumber() + 1 : null);
        yield response(
            "PATIENT_LIST",
            page.hasNext()
                ? "Showing the first 25 matches within your access. Narrow the query to see more."
                : "Matching patients within your access.",
            data);
      }
      case "getPatientSummary" -> {
        long patientId = resolve(a.get("patientQuery"), selected).getId();
        var summary = h.summary(patientId);
        audit.read("PATIENT_VIEWED", "Patient", patientId, "AI");
        yield response("PATIENT_SUMMARY", "Recorded operational history.", summary);
      }
      case "getAvailableRooms", "getRoomOccupancy" -> {
        int n = intArg(a.getOrDefault("minimumFreeBeds", "0"));
        if (n < 0 || n > 100) throw new IllegalArgumentException();
        boolean placementSearch = call.name().equals("getAvailableRooms");
        int minimumFreeBeds = placementSearch ? Math.max(1, n) : n;
        var required = RoomCapabilityMatcher.parse(a.get("requiredCapabilities"));
        var inventory = h.rooms(0);
        var rooms =
            inventory.stream()
                .filter(
                    room ->
                        RoomCapabilityMatcher.missing(required, roomCapabilities(room)).isEmpty())
                .filter(room -> !placementSearch || Boolean.TRUE.equals(room.get("active")))
                .filter(
                    room ->
                        ((Number) room.get("availableBeds")).intValue() >= minimumFreeBeds)
                .toList();
        var excluded =
            inventory.stream()
                .filter(
                    room ->
                        rooms.stream().noneMatch(candidate -> candidate.get("id").equals(room.get("id"))))
                .map(
                    room -> {
                      var missing = RoomCapabilityMatcher.missing(
                          required, roomCapabilities(room));
                      var reasons = new ArrayList<String>();
                      if (!missing.isEmpty()) reasons.add("Missing required capabilities: " + String.join(", ", missing));
                      if (placementSearch && !Boolean.TRUE.equals(room.get("active"))) reasons.add("Room is inactive.");
                      int available = ((Number) room.get("availableBeds")).intValue();
                      if (available < minimumFreeBeds) reasons.add("Only " + available + " beds are available; " + minimumFreeBeds + " required.");
                      return Map.of(
                          "id", room.get("id"),
                          "roomNumber", room.get("roomNumber"),
                          "missingCapabilities", missing,
                          "reason", String.join(" ", reasons));
                    })
                .toList();
        var message =
            rooms.isEmpty()
                ? "No room satisfies the requested availability and capability requirements. See excluded rooms for reasons."
                : "Compatible rooms are listed. Other rooms are excluded with availability or capability reasons.";
        yield response(
            "ROOM_LIST",
            message,
            Map.of("rooms", rooms, "excludedRooms", excluded, "requiredCapabilities", required));
      }
      case "getDoctorPatients" ->
          response(
              "REPORT_RESULT",
              "Current assigned patients.",
              Map.of("admissions", reports.census(null, doctor(a.getOrDefault("doctorQuery", "")).getId())));
      case "getAdmission" ->
          response(
              "REPORT_RESULT",
              "Admission record.",
              h.admissionView(h.admission(longArg(a.get("admissionId")))));
      case "getAdmissions" -> {
        var from = dateArg(a.get("from"));
        var to = dateArg(a.get("to"));
        var zone = departmentTime.zoneId();
        if (from.isAfter(to)) throw new IllegalArgumentException();
        yield response(
            "REPORT_RESULT",
            "Admissions for the requested period.",
            Map.of(
                "admissions",
                h.admissions().stream()
                    .filter(
                        ad ->
                            !ad.getAdmissionDateTime().isBefore(
                                    from.atStartOfDay(zone).toInstant())
                                && ad.getAdmissionDateTime().isBefore(
                                    to.plusDays(1).atStartOfDay(zone).toInstant()))
                    .map(h::admissionView)
                    .toList()));
      }
      case "getProcedureStatistics" -> {
        var today = departmentTime.today();
        yield response(
            "REPORT_RESULT",
            "Procedure totals calculated from saved records.",
            reports.procedures(
                dateArg(a.getOrDefault("from", today.toString())),
                dateArg(a.getOrDefault("to", today.toString())),
                null,
                null));
      }
      case "getDashboardSummary" ->
          response("REPORT_RESULT", "Current department operations.", reports.dashboard());
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
            || !actor.user().getRole().equals("ADMIN") && (route.equals("/app/users") || route.equals("/app/audit")))
          throw new AccessDeniedException("Route not available");
        yield response("NAVIGATION_COMMAND", "Open requested view.", Map.of("route", route));
      }
      case "prepareAdmission", "prepareTransfer", "prepareDischarge" -> {
        var p = resolve(a.get("patientQuery"), selected);
        Long roomId = null, doctorId = null, admissionId = null, version = null;
        String type = call.name().substring(7).toUpperCase();
        Set<String> required = Set.of();
        if (type.equals("ADMISSION")) {
          required = RoomCapabilityMatcher.parse(a.get("requiredRoomCapabilities"));
          if (a.getOrDefault("doctorQuery", "").isBlank())
            yield response(
                "NAVIGATION_COMMAND",
                "Select an attending doctor in the admission form.",
                Map.of("route", "/app/patients/" + p.getPatientIdentifier()));
          doctorId = doctor(a.get("doctorQuery")).getId();
          if (h.admissions().stream()
              .anyMatch(ad -> ad.getPatientId().equals(p.getId()) && ad.getStatus().equals("ACTIVE")))
            throw ApiException.conflict("ALREADY_ADMITTED", "Patient already admitted.");
        } else if (type.equals("TRANSFER")) {
          var active =
              h.admissions().stream()
                  .filter(ad -> ad.getPatientId().equals(p.getId()) && ad.getStatus().equals("ACTIVE"))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new ApiException(
                              400, "NO_ACTIVE_ADMISSION", "No active admission for this patient."));
          admissionId = active.getId();
          version = active.getVersion();
          required = RoomCapabilityMatcher.normalize(active.getRequiredRoomCapabilities());
        }
        if (!type.equals("DISCHARGE")) {
          String number = a.getOrDefault("roomNumber", "");
          if (number.isBlank())
            yield response(
                "NAVIGATION_COMMAND",
                "Select a compatible room and doctor in the standard admission or transfer form.",
                Map.of("route", "/app/patients/" + p.getPatientIdentifier()));
          var matches =
              h.rooms(0).stream()
                  .filter(r -> number.equals(r.get("roomNumber")))
                  .toList();
          if (matches.size() != 1)
            throw ApiException.conflict("ROOM_CAPACITY_EXCEEDED", "That room is unavailable.");
          roomId = ((Number) matches.getFirst().get("id")).longValue();
          var selectedRoom = h.room(roomId);
          if (!selectedRoom.isActive())
            throw ApiException.conflict("ROOM_INACTIVE", "Room " + number + " is inactive.");
          RoomCapabilityMatcher.require(selectedRoom, required);
          if (h.occupied(roomId) >= selectedRoom.getBedCount())
            throw ApiException.conflict(
                "ROOM_CAPACITY_EXCEEDED", "Room " + number + " has no available capacity.");
        }
        yield response(
            "CONFIRMATION_CARD",
            "Review this proposal. No hospital records have changed.",
            actions.prepare(
                type,
                new AiActionService.Payload(
                    p.getId(), admissionId, roomId, doctorId, version,
                    "User-requested AI transfer", List.copyOf(required))));
      }
      default -> throw new IllegalArgumentException();
      };
    } catch (DateTimeParseException | IllegalArgumentException | NullPointerException e) {
      throw invalidToolCall();
    }
  }

  private static ApiException invalidToolCall() {
    return new ApiException(
        400, "INVALID_TOOL_CALL", "The assistant returned an invalid tool request.");
  }

  private static int intArg(String raw) {
    if (raw == null || raw.isBlank()) throw invalidToolCall();
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw invalidToolCall();
    }
  }

  private static long longArg(String raw) {
    if (raw == null || raw.isBlank()) throw invalidToolCall();
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      throw invalidToolCall();
    }
  }

  private static LocalDate dateArg(String raw) {
    if (raw == null || raw.isBlank()) throw invalidToolCall();
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw invalidToolCall();
    }
  }
}
