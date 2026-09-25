package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.security.*;
import com.example.hospital.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.util.*;
import org.hibernate.Session;
import org.springframework.stereotype.Service;

/** Validated composition of existing business operations; called inside action transactions. */
@Service
public class AiWorkflowService {
  public record Citation(
      String sourceId,
      String sourceName,
      String location,
      String excerpt,
      boolean verified,
      Integer characterStart,
      Integer characterEnd,
      String verifiedLocation) {
    public Citation(String sourceId, String sourceName, String location, String excerpt) {
      this(sourceId, sourceName, location, excerpt, false, null, null, null);
    }
  }
  public record FieldEvidence(
      String status,
      double confidence,
      List<Citation> sources,
      List<Citation> conflicts,
      boolean requiresDecision) {
    public FieldEvidence(String status, double confidence, List<Citation> sources, List<Citation> conflicts) {
      this(status, confidence, sources, conflicts, false);
    }
  }
  public record Step(
      String key, String operation, String source, ObjectNode fields, Map<String, FieldEvidence> evidence) {
    public Step(String key, String operation, String source, ObjectNode fields) {
      this(key, operation, source, fields, Map.of());
    }
  }
  public record Plan(String title, List<Step> steps) {}
  public record FieldDecision(String stepKey, String field, String decision, Object value) {}
  private final ObjectMapper json;
  private final Validator validator;
  private final AiSourceService sources;
  private final HospitalService hospital;
  private final PatientService patients;
  private final CatalogueService catalogue;
  private final StayService stays;
  private final WorkspaceService workspaces;
  private final Actor actor;
  private final EntityManager em;

  public AiWorkflowService(ObjectMapper json, Validator validator, AiSourceService sources, HospitalService hospital,
      PatientService patients, CatalogueService catalogue, StayService stays,
      WorkspaceService workspaces, Actor actor, EntityManager em) {
    this.json = json; this.validator = validator; this.sources = sources; this.hospital = hospital;
    this.patients = patients; this.catalogue = catalogue; this.stays = stays;
    this.workspaces = workspaces; this.actor = actor; this.em = em;
  }

  public static final String DESCRIPTION = """
      Prepare one atomic workflow for human review. plan is a JSON STRING:
      {"title":"Short goal","steps":[{"key":"p1","operation":"createPatient","source":"filename or user request","fields":{...},"evidence":{"firstName":{"status":"SUPPORTED","confidence":0.94,"sources":[{"sourceId":"uploaded source id","location":"row 2, column B","excerpt":"Jane"}],"conflicts":[]}}}]}.
      For file-derived steps, cite the exact attached filename in source and include evidence keyed by every file-derived field. Each evidence status is SUPPORTED, UNCERTAIN, CONFLICT or UNRESOLVED; confidence is 0..1. Cite only source IDs and exact excerpts from attached files. A citation has sourceId, location and excerpt. Put contradictory citations in conflicts. Do not invent source IDs, locations or excerpts. If evidence is missing, uncertain or conflicting, say so; the reviewer must explicitly accept or edit that field before confirmation. The server marks fields without evidence as UNRESOLVED when the step cites an attached filename.
      Maximum 50 steps. Never invent missing required fields; ask via respond instead.
      Operations and fields (only these fields):
      createHospital: name,departmentName (only first step; subsequent records go in its new department);
      createPatient: patientIdentifier,firstName,lastName,dateOfBirth (YYYY-MM-DD),address?,phoneNumber?;
      createDoctor: doctorIdentifier,firstName,lastName,specialty,active (boolean);
      createRoom: roomNumber,bedCount (1..100),active (boolean),capabilities? (array of configured room tags);
      createProcedure: procedureCode,procedureName,currentCost (number),active (boolean);
      admit: patientId,doctorId,roomId,requiredRoomCapabilities? (array of required tags);
      transfer: admissionId,roomId,reason,version;
      discharge: admissionId,version;
      recordProcedure: admissionId,medicalProcedureId,doctorId,performedAt (ISO instant),note?.
      A numeric ID must come from a query result. For IDs from earlier steps use "$key" strings.
      E.g. admit fields {"patientId":"$p1","doctorId":"$d1","roomId":"$r1"}.
      Doctors/rooms/procedure catalogue creation requires department ADMIN, including a newly created hospital.
      No account creation, permissions, deletions, shell commands or arbitrary API URLs.
      Cite source filenames per step. Do not infer clinical decisions from documents.
      """;

  private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
      Map.entry("createHospital", Set.of("name", "departmentName")),
      Map.entry("createPatient", Set.of("patientIdentifier", "firstName", "lastName", "dateOfBirth", "address", "phoneNumber")),
      Map.entry("createDoctor", Set.of("doctorIdentifier", "firstName", "lastName", "specialty", "active")),
      Map.entry("createRoom", Set.of("roomNumber", "bedCount", "active", "capabilities")),
      Map.entry("createProcedure", Set.of("procedureCode", "procedureName", "currentCost", "active")),
      Map.entry("admit", Set.of("patientId", "doctorId", "roomId", "requiredRoomCapabilities")),
      Map.entry("transfer", Set.of("admissionId", "roomId", "reason", "version")),
      Map.entry("discharge", Set.of("admissionId", "version")),
      Map.entry("recordProcedure", Set.of("admissionId", "medicalProcedureId", "doctorId", "performedAt", "note")));

  public Plan parse(String text) {
    return parse(text, List.of(), List.of());
  }

  public Plan parse(String text, List<String> fileSourceNames) {
    return parse(text, fileSourceNames, List.of());
  }

  public Plan parse(String text, List<String> fileSourceNames, List<String> attachedSourceIds) {
    if (text == null || text.length() > 50000) throw invalid();
    try {
      Plan plan = withDefaultEvidence(json.readValue(text, Plan.class));
      validate(plan);
      return withVerifiedEvidence(withMissingFileEvidence(plan, fileSourceNames), attachedSourceIds);
    } catch (ApiException | org.springframework.security.access.AccessDeniedException e) { throw e; }
    catch (Exception e) { throw invalid(); }
  }

  /** Reads a server-stored proposal; citation verification was computed when it was prepared. */
  public Plan parseStored(String text) {
    if (text == null || text.length() > 50000) throw invalid();
    try {
      Plan plan = withDefaultEvidence(json.readValue(text, Plan.class));
      validate(plan);
      validateStoredEvidence(plan);
      return plan;
    } catch (ApiException | org.springframework.security.access.AccessDeniedException e) { throw e; }
    catch (Exception e) { throw invalid(); }
  }

  private static ApiException invalid() {
    return new ApiException(400, "INVALID_WORKFLOW", "The workflow has missing or invalid fields or references. Ask the assistant to correct the proposal.");
  }

  public void validate(Plan plan) {
    actor.staff();
    if (plan == null || plan.title() == null || plan.title().isBlank() || plan.title().length() > 200
        || plan.steps() == null || plan.steps().isEmpty() || plan.steps().size() > 50) throw invalid();
    Map<String, String> keys = new HashMap<>();
    Map<String, PlannedRoom> plannedRooms = new HashMap<>();
    Map<String, Set<String>> plannedAdmissionRequirements = new HashMap<>();
    Map<String, Integer> occupancyChanges = new HashMap<>();
    Map<String, String> plannedAdmissionRooms = new HashMap<>();
    boolean newHospital = false;
    for (var step : plan.steps()) {
      if (step == null || step.key() == null || !step.key().matches("[a-zA-Z][a-zA-Z0-9_]{0,39}")
          || keys.containsKey(step.key()) || step.operation() == null || !FIELDS.containsKey(step.operation())
          || step.source() == null || step.source().isBlank() || step.source().length() > 300 || step.fields() == null) throw invalid();
      step.fields().fieldNames().forEachRemaining(k -> { if (!FIELDS.get(step.operation()).contains(k)) throw invalid(); });
      validateEvidenceInput(step);
      if (step.operation().equals("createHospital")) {
        if (!keys.isEmpty()) throw invalid();
        newHospital = true;
      }
      if (!newHospital && Set.of("createDoctor", "createRoom", "createProcedure").contains(step.operation())) actor.admin();
      ObjectNode resolved = step.fields().deepCopy();
      final boolean inNewHospital = newHospital;
      resolved.fields().forEachRemaining(e -> {
        if (e.getValue().isTextual() && e.getValue().asText().startsWith("$") && e.getKey().endsWith("Id")) {
          String refType = keys.get(e.getValue().asText().substring(1));
          String expected = switch (e.getKey()) {
            case "patientId" -> "createPatient"; case "doctorId" -> "createDoctor";
            case "roomId" -> "createRoom"; case "medicalProcedureId" -> "createProcedure";
            case "admissionId" -> "admit"; default -> "";
          };
          if (!expected.equals(refType)) throw invalid();
          resolved.put(e.getKey(), 1L);
        } else if (e.getKey().endsWith("Id") && (!e.getValue().isIntegralNumber() || e.getValue().asLong() <= 0 || inNewHospital)) {
          // Existing IDs cannot be imported into a freshly created department.
          throw invalid();
        }
      });
      Object validated = input(step.operation(), resolved);
      switch (step.operation()) {
        case "createRoom" -> {
          var room = (RoomInput) validated;
          var capabilities = RoomCapabilityMatcher.normalize(room.capabilities());
          plannedRooms.put(
              step.key(),
              new PlannedRoom(
                  room.roomNumber(), room.bedCount(), room.active(), capabilities));
        }
        case "admit" -> {
          var admission = (AdmissionInput) validated;
          var required =
              RoomCapabilityMatcher.normalize(admission.requiredRoomCapabilities());
          String roomKey =
              requireRoomPlacement(
                  step.fields().get("roomId"), plannedRooms, occupancyChanges, required);
          occupancyChanges.merge(roomKey, 1, Integer::sum);
          plannedAdmissionRequirements.put(step.key(), required);
          plannedAdmissionRooms.put("plannedAdmission:" + step.key(), roomKey);
        }
        case "transfer" -> {
          JsonNode admissionReference = step.fields().get("admissionId");
          String admissionKey = admissionStateKey(admissionReference);
          String currentRoomKey =
              currentRoomKey(admissionReference, admissionKey, plannedAdmissionRooms);
          var required = requirementsForAdmission(admissionReference, plannedAdmissionRequirements);
          String destinationRoomKey =
              requireRoomPlacement(
                  step.fields().get("roomId"), plannedRooms, occupancyChanges, required);
          if (destinationRoomKey.equals(currentRoomKey))
            throw ApiException.conflict("SAME_ROOM", "The patient is already in that room.");
          occupancyChanges.merge(currentRoomKey, -1, Integer::sum);
          occupancyChanges.merge(destinationRoomKey, 1, Integer::sum);
          plannedAdmissionRooms.put(admissionKey, destinationRoomKey);
        }
        default -> { }
      }
      keys.put(step.key(), step.operation());
    }
  }

  private static void validateEvidenceInput(Step step) {
    if (step.evidence() == null) return;
    if (step.evidence().size() > FIELDS.get(step.operation()).size()) throw invalid();
    for (var entry : step.evidence().entrySet()) {
      String field = entry.getKey();
      FieldEvidence evidence = entry.getValue();
      if (field == null || !FIELDS.get(step.operation()).contains(field) || !step.fields().has(field)
          || evidence == null || !Set.of("SUPPORTED", "UNCERTAIN", "CONFLICT", "UNRESOLVED").contains(evidence.status())
          || !Double.isFinite(evidence.confidence()) || evidence.confidence() < 0 || evidence.confidence() > 1
          || !validCitations(evidence.sources()) || !validCitations(evidence.conflicts())) throw invalid();
    }
  }

  private static Plan withDefaultEvidence(Plan plan) {
    if (plan == null || plan.steps() == null) return plan;
    var steps = new ArrayList<Step>();
    for (Step step : plan.steps()) {
      if (step != null && step.evidence() == null) {
        steps.add(new Step(step.key(), step.operation(), step.source(), step.fields(), Map.of()));
      } else if (step != null) {
        var evidence = new LinkedHashMap<String, FieldEvidence>();
        step.evidence().forEach((field, value) -> evidence.put(field, value == null ? null
            : new FieldEvidence(value.status(), value.confidence(), empty(value.sources()),
                empty(value.conflicts()), value.requiresDecision())));
        steps.add(new Step(step.key(), step.operation(), step.source(), step.fields(), evidence));
      } else steps.add(null);
    }
    return new Plan(plan.title(), steps);
  }

  private static <T> List<T> empty(List<T> values) { return values == null ? List.of() : values; }

  private static boolean validCitations(List<Citation> citations) {
    if (citations == null) return true;
    if (citations.size() > 10) return false;
    for (Citation citation : citations) {
      if (citation == null || citation.location() == null || citation.location().isBlank() || citation.location().length() > 160
          || citation.excerpt() == null || citation.excerpt().isBlank() || citation.excerpt().length() > 500
          || citation.sourceId() != null && citation.sourceId().length() > 64
          || citation.sourceName() != null && citation.sourceName().length() > 200) return false;
    }
    return true;
  }

  private Plan withVerifiedEvidence(Plan plan, List<String> attachedSourceIds) {
    Set<String> authorizedSources = attachedSourceIds == null ? Set.of()
        : attachedSourceIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
    List<Step> steps = new ArrayList<>();
    for (Step step : plan.steps()) {
      var evidence = new LinkedHashMap<String, FieldEvidence>();
      for (var entry : step.evidence().entrySet()) {
        FieldEvidence original = entry.getValue();
        List<Citation> verifiedSources = original.sources().stream().map(c -> verifyCitation(c, authorizedSources)).toList();
        List<Citation> verifiedConflicts = original.conflicts().stream().map(c -> verifyCitation(c, authorizedSources)).toList();
        boolean requiresDecision = requiresDecision(original.status(), original.confidence(), verifiedSources, verifiedConflicts);
        evidence.put(entry.getKey(), new FieldEvidence(original.status(), original.confidence(),
            verifiedSources, verifiedConflicts, requiresDecision));
      }
      steps.add(new Step(step.key(), step.operation(), step.source(), step.fields(), Map.copyOf(evidence)));
    }
    return new Plan(plan.title(), List.copyOf(steps));
  }

  private static Plan withMissingFileEvidence(Plan plan, List<String> fileSourceNames) {
    List<String> filenames = fileSourceNames == null ? List.of() : fileSourceNames.stream()
        .filter(Objects::nonNull).map(AiWorkflowService::basename).filter(name -> !name.isBlank()).toList();
    if (filenames.isEmpty()) return plan;
    var steps = new ArrayList<Step>();
    for (Step step : plan.steps()) {
      if (!citesAttachedFile(step.source(), filenames)) {
        steps.add(step);
        continue;
      }
      var evidence = new LinkedHashMap<>(step.evidence());
      var fields = step.fields().fieldNames();
      while (fields.hasNext()) {
        String field = fields.next();
        evidence.putIfAbsent(field, new FieldEvidence("UNRESOLVED", 0, List.of(), List.of()));
      }
      steps.add(new Step(step.key(), step.operation(), step.source(), step.fields(), evidence));
    }
    return new Plan(plan.title(), List.copyOf(steps));
  }

  private static boolean citesAttachedFile(String citation, List<String> filenames) {
    String value = citation.replace('\\', '/').toLowerCase(Locale.ROOT);
    String base = basename(value).toLowerCase(Locale.ROOT);
    return filenames.stream().anyMatch(name -> {
      String candidate = name.toLowerCase(Locale.ROOT);
      return base.equals(candidate) || value.contains(candidate);
    });
  }

  private static String basename(String name) {
    String normalized = name.replace('\\', '/').strip();
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }

  private Citation verifyCitation(Citation citation, Set<String> authorizedSources) {
    String sourceName = null;
    Integer start = null;
    Integer end = null;
    boolean verified = false;
    String verifiedLocation = null;
    if (citation.sourceId() != null && !citation.sourceId().isBlank()
        && authorizedSources.contains(citation.sourceId())) {
      try {
        AiSourceService.Source source = sources.sourceForExtraction(citation.sourceId());
        sourceName = source.name();
        int found = source.text().indexOf(citation.excerpt());
        if (found >= 0) {
          start = found;
          end = found + citation.excerpt().length();
          verified = true;
          verifiedLocation = source.locationFor(start, end);
        }
      } catch (ApiException unavailable) {
        // Keep the reported citation visible, but never present it as verified.
      }
    }
    return new Citation(citation.sourceId(), sourceName, citation.location(), citation.excerpt(),
        verified, start, end, verifiedLocation);
  }

  private static boolean requiresDecision(String status, double confidence, List<Citation> sources, List<Citation> conflicts) {
    return !"SUPPORTED".equals(status) || confidence < 0.65 || sources.isEmpty()
        || !conflicts.isEmpty() || sources.stream().anyMatch(c -> !c.verified())
        || conflicts.stream().anyMatch(c -> !c.verified());
  }

  private static void validateStoredEvidence(Plan plan) {
    for (Step step : plan.steps()) {
      for (FieldEvidence evidence : step.evidence().values()) {
        boolean required = requiresDecision(evidence.status(), evidence.confidence(), evidence.sources(), evidence.conflicts());
        if (evidence.requiresDecision() != required) throw invalid();
        for (Citation citation : concat(evidence.sources(), evidence.conflicts())) {
          if (citation.verified() && (citation.sourceName() == null || citation.characterStart() == null
              || citation.characterEnd() == null || citation.characterStart() < 0
              || citation.characterEnd() < citation.characterStart()
              || citation.characterEnd() - citation.characterStart() != citation.excerpt().length())) throw invalid();
          if (!citation.verified() && (citation.characterStart() != null || citation.characterEnd() != null)) throw invalid();
        }
      }
    }
  }

  private static List<Citation> concat(List<Citation> first, List<Citation> second) {
    var all = new ArrayList<Citation>(first);
    all.addAll(second);
    return all;
  }

  public Plan resolveFieldDecisions(Plan plan, List<FieldDecision> decisions) {
    if (decisions == null || decisions.size() > 300) throw invalidDecision();
    var supplied = new LinkedHashMap<String, FieldDecision>();
    for (FieldDecision decision : decisions) {
      if (decision == null || decision.stepKey() == null || decision.field() == null) throw invalidDecision();
      String key = decisionKey(decision.stepKey(), decision.field());
      if (supplied.putIfAbsent(key, decision) != null) throw invalidDecision();
    }
    var expected = new HashSet<String>();
    var result = new ArrayList<Step>();
    for (Step step : plan.steps()) {
      ObjectNode fields = step.fields().deepCopy();
      for (var entry : step.evidence().entrySet()) {
        if (!entry.getValue().requiresDecision()) continue;
        String key = decisionKey(step.key(), entry.getKey());
        expected.add(key);
        FieldDecision decision = supplied.get(key);
        if (decision == null) throw reviewRequired();
        if ("ACCEPTED".equals(decision.decision())) {
          if (decision.value() != null) throw invalidDecision();
        } else if ("EDITED".equals(decision.decision())) {
          if (decision.value() == null) throw invalidDecision();
          JsonNode edited = json.valueToTree(decision.value());
          if (edited == null || edited.isNull() || edited.toString().length() > 2000) throw invalidDecision();
          fields.set(entry.getKey(), edited);
        } else throw invalidDecision();
      }
      result.add(new Step(step.key(), step.operation(), step.source(), fields, step.evidence()));
    }
    if (!expected.equals(supplied.keySet())) throw invalidDecision();
    Plan resolved = new Plan(plan.title(), List.copyOf(result));
    validate(resolved);
    return resolved;
  }

  private static String decisionKey(String stepKey, String field) { return stepKey + "\u0000" + field; }

  private static ApiException reviewRequired() {
    return ApiException.conflict("WORKFLOW_REVIEW_REQUIRED", "Accept or edit every uncertain, conflicting, or unverified field before confirming this workflow.");
  }

  private static ApiException invalidDecision() {
    return new ApiException(400, "INVALID_WORKFLOW_DECISION", "The field decisions do not match the fields that require review.");
  }

  private record PlannedRoom(
      String roomNumber, int bedCount, boolean active, Set<String> capabilities) {}

  private String requireRoomPlacement(
      JsonNode roomReference,
      Map<String, PlannedRoom> plannedRooms,
      Map<String, Integer> occupancyChanges,
      Set<String> requiredCapabilities) {
    String key = referenceKey(roomReference);
    if (key != null) {
      PlannedRoom room = plannedRooms.get(key);
      if (room == null) throw invalid();
      if (!room.active())
        throw ApiException.conflict("ROOM_INACTIVE", "Room " + room.roomNumber() + " is inactive.");
      RoomCapabilityMatcher.require(
          room.roomNumber(), room.capabilities(), requiredCapabilities);
      String roomKey = "planned:" + key;
      if (occupancyChanges.getOrDefault(roomKey, 0) >= room.bedCount())
        throw roomFull(room.roomNumber());
      return roomKey;
    }
    if (roomReference == null || !roomReference.isIntegralNumber() || roomReference.asLong() <= 0)
      throw invalid();
    Room room = hospital.room(roomReference.asLong());
    if (!room.isActive())
      throw ApiException.conflict("ROOM_INACTIVE", "Room " + room.getRoomNumber() + " is inactive.");
    RoomCapabilityMatcher.require(room, requiredCapabilities);
    String roomKey = "existing:" + room.getId();
    if (hospital.occupied(room.getId()) + occupancyChanges.getOrDefault(roomKey, 0)
        >= room.getBedCount()) throw roomFull(room.getRoomNumber());
    return roomKey;
  }

  private static ApiException roomFull(String roomNumber) {
    return ApiException.conflict(
        "ROOM_CAPACITY_EXCEEDED", "Room " + roomNumber + " has no available capacity.");
  }

  private String currentRoomKey(
      JsonNode admissionReference,
      String admissionKey,
      Map<String, String> plannedAdmissionRooms) {
    String plannedRoomKey = plannedAdmissionRooms.get(admissionKey);
    if (plannedRoomKey != null) return plannedRoomKey;
    if (referenceKey(admissionReference) != null) throw invalid();
    if (admissionReference == null
        || !admissionReference.isIntegralNumber()
        || admissionReference.asLong() <= 0) throw invalid();
    return "existing:" + hospital.roomIdForAdmission(admissionReference.asLong());
  }

  private static String admissionStateKey(JsonNode admissionReference) {
    String key = referenceKey(admissionReference);
    if (key != null) return "plannedAdmission:" + key;
    if (admissionReference == null
        || !admissionReference.isIntegralNumber()
        || admissionReference.asLong() <= 0) throw invalid();
    return "existingAdmission:" + admissionReference.asLong();
  }

  private Set<String> requirementsForAdmission(
      JsonNode admissionReference, Map<String, Set<String>> plannedAdmissionRequirements) {
    String key = referenceKey(admissionReference);
    if (key != null) {
      Set<String> required = plannedAdmissionRequirements.get(key);
      if (required == null) throw invalid();
      return required;
    }
    if (admissionReference == null
        || !admissionReference.isIntegralNumber()
        || admissionReference.asLong() <= 0) throw invalid();
    Admission admission = hospital.admission(admissionReference.asLong());
    if (!admission.getStatus().equals("ACTIVE"))
      throw ApiException.conflict("ADMISSION_CLOSED", "This admission is already closed.");
    return RoomCapabilityMatcher.normalize(admission.getRequiredRoomCapabilities());
  }

  private static String referenceKey(JsonNode value) {
    if (value == null || !value.isTextual() || !value.asText().startsWith("$")) return null;
    return value.asText().substring(1);
  }

  private Object input(String operation, ObjectNode fields) {
    if (operation.equals("createHospital")) {
      for (String key : List.of("name", "departmentName")) {
        var n = fields.get(key);
        if (n == null || !n.isTextual() || n.asText().isBlank() || n.asText().length() > 120) throw invalid();
      }
      return fields;
    }
    for (String key : List.of("active")) {
      if (FIELDS.get(operation).contains(key) && (!fields.has(key) || !fields.get(key).isBoolean())) throw invalid();
    }
    if (operation.equals("transfer") || operation.equals("discharge") || operation.equals("recordProcedure")) {
      if (!fields.has("admissionId") || !fields.get("admissionId").isIntegralNumber() || fields.get("admissionId").asLong() <= 0) throw invalid();
      fields = fields.deepCopy();
      fields.remove("admissionId");
    }
    Class<?> type = switch (operation) {
      case "createPatient" -> PatientInput.class; case "createDoctor" -> DoctorInput.class;
      case "createRoom" -> RoomInput.class; case "createProcedure" -> ProcedureInput.class;
      case "admit" -> AdmissionInput.class; case "transfer" -> TransferInput.class;
      case "discharge" -> DischargeInput.class; case "recordProcedure" -> RecordProcedureInput.class;
      default -> throw invalid();
    };
    Object value = json.convertValue(fields, type);
    if (!validator.validate(value).isEmpty()) throw invalid();
    return value;
  }

  public Object execute(Plan plan) {
    validate(plan);
    var original = DepartmentContext.current();
    Map<String, Long> ids = new LinkedHashMap<>();
    Long destination = null;
    try {
      for (var step : plan.steps()) {
        ObjectNode f = step.fields().deepCopy();
        f.fields().forEachRemaining(e -> {
          if (e.getKey().endsWith("Id") && e.getValue().isTextual() && e.getValue().asText().startsWith("$"))
            f.put(e.getKey(), ids.get(e.getValue().asText().substring(1)));
        });
        Object value = input(step.operation(), f);
        Long id;
        switch (step.operation()) {
          case "createHospital" -> {
            var created = workspaces.createHospital(f.get("name").asText(), f.get("departmentName").asText());
            id = ((Number) created.get("hospitalId")).longValue();
            destination = ((Number) created.get("departmentId")).longValue();
            DepartmentContext.set(new DepartmentContext.Scope(destination, "ADMIN", null));
            em.unwrap(Session.class).enableFilter("department").setParameter("departmentId", destination);
          }
          case "createPatient" -> id = patients.save(null, (PatientInput) value).getId();
          case "createDoctor" -> id = catalogue.saveDoctor(null, (DoctorInput) value).getId();
          case "createRoom" -> id = catalogue.saveRoom(null, (RoomInput) value).getId();
          case "createProcedure" -> id = catalogue.saveProcedure(null, (ProcedureInput) value).getId();
          case "admit" -> id = stays.create((AdmissionInput) value, "AI").getId();
          case "transfer" -> {
            id = f.get("admissionId").asLong();
            stays.move(id, (TransferInput) value, "AI");
          }
          case "discharge" -> {
            id = f.get("admissionId").asLong();
            stays.close(id, ((DischargeInput) value).version(), "AI");
          }
          case "recordProcedure" -> id = stays.record(f.get("admissionId").asLong(), (RecordProcedureInput) value).getId();
          default -> throw invalid();
        }
        ids.put(step.key(), id);
      }
      return destination == null ? Map.of("created", ids) : Map.of("created", ids, "departmentId", destination);
    } finally {
      if (original == null) DepartmentContext.clear(); else DepartmentContext.set(original);
      em.unwrap(Session.class).enableFilter("department").setParameter("departmentId", DepartmentContext.id());
    }
  }
}
