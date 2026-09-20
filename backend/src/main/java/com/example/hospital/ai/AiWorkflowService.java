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
  public record Step(String key, String operation, String source, ObjectNode fields) {}
  public record Plan(String title, List<Step> steps) {}
  private final ObjectMapper json;
  private final Validator validator;
  private final HospitalService hospital;
  private final WorkspaceService workspaces;
  private final Actor actor;
  private final EntityManager em;

  public AiWorkflowService(ObjectMapper json, Validator validator, HospitalService hospital,
      WorkspaceService workspaces, Actor actor, EntityManager em) {
    this.json = json; this.validator = validator; this.hospital = hospital;
    this.workspaces = workspaces; this.actor = actor; this.em = em;
  }

  public static final String DESCRIPTION = """
      Prepare one atomic workflow for human review. plan is a JSON STRING:
      {"title":"Short goal","steps":[{"key":"p1","operation":"createPatient","source":"filename or user request","fields":{...}}]}.
      Maximum 50 steps. Never invent missing required fields; ask via respond instead.
      Operations and fields (only these fields):
      createHospital: name,departmentName (only first step; subsequent records go in its new department);
      createPatient: patientIdentifier,firstName,lastName,dateOfBirth (YYYY-MM-DD),address?,phoneNumber?;
      createDoctor: doctorIdentifier,firstName,lastName,specialty,active (boolean);
      createRoom: roomNumber,bedCount (1..100),active (boolean);
      createProcedure: procedureCode,procedureName,currentCost (number),active (boolean);
      admit: patientId,doctorId,roomId;
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
      Map.entry("createRoom", Set.of("roomNumber", "bedCount", "active")),
      Map.entry("createProcedure", Set.of("procedureCode", "procedureName", "currentCost", "active")),
      Map.entry("admit", Set.of("patientId", "doctorId", "roomId")),
      Map.entry("transfer", Set.of("admissionId", "roomId", "reason", "version")),
      Map.entry("discharge", Set.of("admissionId", "version")),
      Map.entry("recordProcedure", Set.of("admissionId", "medicalProcedureId", "doctorId", "performedAt", "note")));

  public Plan parse(String text) {
    if (text == null || text.length() > 50000) throw invalid();
    try {
      Plan plan = json.readValue(text, Plan.class);
      validate(plan);
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
    boolean newHospital = false;
    for (var step : plan.steps()) {
      if (step == null || step.key() == null || !step.key().matches("[a-zA-Z][a-zA-Z0-9_]{0,39}")
          || keys.containsKey(step.key()) || step.operation() == null || !FIELDS.containsKey(step.operation())
          || step.source() == null || step.source().isBlank() || step.source().length() > 300 || step.fields() == null) throw invalid();
      step.fields().fieldNames().forEachRemaining(k -> { if (!FIELDS.get(step.operation()).contains(k)) throw invalid(); });
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
      input(step.operation(), resolved);
      keys.put(step.key(), step.operation());
    }
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
          case "createPatient" -> id = hospital.savePatient(null, (PatientInput) value).id;
          case "createDoctor" -> id = hospital.saveDoctor(null, (DoctorInput) value).id;
          case "createRoom" -> id = hospital.saveRoom(null, (RoomInput) value).id;
          case "createProcedure" -> id = hospital.saveProcedure(null, (ProcedureInput) value).id;
          case "admit" -> id = hospital.admit((AdmissionInput) value, "AI").id;
          case "transfer" -> {
            id = f.get("admissionId").asLong();
            hospital.transfer(id, (TransferInput) value, "AI");
          }
          case "discharge" -> {
            id = f.get("admissionId").asLong();
            hospital.discharge(id, ((DischargeInput) value).version(), "AI");
          }
          case "recordProcedure" -> id = hospital.recordProcedure(f.get("admissionId").asLong(), (RecordProcedureInput) value).id;
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
