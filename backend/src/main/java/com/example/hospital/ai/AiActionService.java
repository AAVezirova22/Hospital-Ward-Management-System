package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiActionService {
  public record WorkflowConfirmationInput(List<AiWorkflowService.FieldDecision> fieldDecisions) {}
  private final AiPendingActionRepository actions;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final HospitalService h;
  private final StayService stays;
  private final AuditService audit;
  private final ObjectMapper json;
  private final int ttl;
  private final AiWorkflowService workflows;

  public AiActionService(
      AiPendingActionRepository a,
      WorkflowLockRepository l,
      Actor actor,
      HospitalService h,
      StayService stays,
      AuditService au,
      ObjectMapper j,
      @Value("${app.ai.action-ttl-seconds}") int ttl,
      AiWorkflowService workflows) {
    actions = a;
    lock = l;
    this.actor = actor;
    this.h = h;
    this.stays = stays;
    audit = au;
    json = j;
    this.ttl = ttl;
    this.workflows = workflows;
  }

  public static class ExpiredActionException extends ApiException {
    public ExpiredActionException() {
      super(409, "ACTION_EXPIRED", "This proposal expired. Prepare a new one.");
    }
  }

  public record Payload(
      Long patientId,
      Long admissionId,
      Long roomId,
      Long doctorId,
      Long version,
      String reason,
      List<String> requiredRoomCapabilities) {
    public Payload(
        Long patientId, Long admissionId, Long roomId, Long doctorId, Long version, String reason) {
      this(patientId, admissionId, roomId, doctorId, version, reason, List.of());
    }
  }

  public AiPendingAction get(Long id) {
    var a = actions.findById(id).orElseThrow(ApiException::missing);
    if (!a.getUserId().equals(actor.user().getId()))
      throw new AccessDeniedException("Action belongs to another user");
    return a;
  }

  @Transactional
  public Object prepare(String type, Payload p) {
    actor.staff();
    var a = new AiPendingAction();
    a.setUserId(actor.user().getId());
    a.setActionType(type);
    a.setExpiresAt(Instant.now().plusSeconds(ttl));
    try {
      a.setPayload(json.writeValueAsString(p));
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_PREPARED", "AiPendingAction", a.getId(), "AI");
    return card(a, p);
  }

  public Object card(AiPendingAction a, Payload p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("action", Views.pendingAction(a));
    m.put("patient", Views.patient(h.patient(p.patientId())));
    if (p.roomId() != null)
      m.put(
          "destination",
          h.rooms(0).stream()
              .filter(room -> room.get("id").equals(p.roomId()))
              .findFirst()
              .orElseThrow(ApiException::missing));
    m.put(
        "requiredRoomCapabilities",
        p.requiredRoomCapabilities() == null ? List.of() : p.requiredRoomCapabilities());
    if (p.admissionId() != null) m.put("current", h.admissionView(h.admission(p.admissionId())));
    if (p.doctorId() != null)
      m.put(
          "doctor",
          Views.doctor(
              h.doctors().stream()
                  .filter(d -> d.getId().equals(p.doctorId()))
                  .findFirst()
                  .orElseThrow()));
    return m;
  }

  @Transactional
  public Object prepareWorkflow(String text) {
    return prepareWorkflow(text, List.of());
  }

  @Transactional
  public Object prepareWorkflow(String text, List<String> fileSourceNames) {
    return prepareWorkflow(text, fileSourceNames, List.of());
  }

  public Object prepareWorkflow(String text, List<String> fileSourceNames, List<String> attachedSourceIds) {
    var plan = workflows.parse(text, fileSourceNames, attachedSourceIds);
    var a = new AiPendingAction();
    a.setUserId(actor.user().getId());
    a.setActionType("WORKFLOW");
    a.setExpiresAt(Instant.now().plusSeconds(ttl));
    try { a.setPayload(json.writeValueAsString(plan)); }
    catch (Exception e) { throw new IllegalArgumentException(); }
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_PREPARED", "AiPendingAction", a.getId(), "AI");
    return Map.of("action", Views.pendingAction(a), "workflow", workflowView(plan));
  }

  private Map<String, Object> workflowView(AiWorkflowService.Plan plan) {
    var steps =
        plan.steps().stream()
            .map(
                step ->
                    Map.of(
                        "key", step.key(),
                        "operation", step.operation(),
                        "source", step.source(),
                        "fields",
                            json.convertValue(
                                step.fields(), new TypeReference<Map<String, Object>>() {}),
                        "evidence", evidenceView(step.evidence())))
            .toList();
    return Map.of("title", plan.title(), "steps", steps);
  }

  private Map<String, Object> evidenceView(Map<String, AiWorkflowService.FieldEvidence> evidence) {
    var result = new LinkedHashMap<String, Object>();
    evidence.forEach((field, item) -> {
      var view = new LinkedHashMap<String, Object>();
      view.put("status", item.status());
      view.put("confidence", item.confidence());
      view.put("requiresDecision", item.requiresDecision());
      view.put("sources", item.sources().stream().map(this::citationView).toList());
      view.put("conflicts", item.conflicts().stream().map(this::citationView).toList());
      result.put(field, view);
    });
    return result;
  }

  private Map<String, Object> citationView(AiWorkflowService.Citation citation) {
    var view = new LinkedHashMap<String, Object>();
    if (citation.sourceId() != null) view.put("sourceId", citation.sourceId());
    view.put("sourceName", citation.sourceName() == null ? "Unverified source" : citation.sourceName());
    view.put("location", citation.verified()
        ? "characters " + citation.characterStart() + "-" + citation.characterEnd()
        : "Unverified location");
    view.put("reportedLocation", citation.location());
    view.put("excerpt", citation.excerpt());
    view.put("verified", citation.verified());
    if (citation.verified()) {
      view.put("characterStart", citation.characterStart());
      view.put("characterEnd", citation.characterEnd());
    }
    return view;
  }

  public Object confirm(Long id) { return confirm(id, null); }

  @Transactional(noRollbackFor = ExpiredActionException.class)
  public Object confirm(Long id, WorkflowConfirmationInput input) {
    lock.acquire();
    actor.staff();
    var a = get(id);
    if (!a.getStatus().equals("PENDING"))
      throw ApiException.conflict("ACTION_CONSUMED", "This action is no longer pending.");
    if (Instant.now().isAfter(a.getExpiresAt())) {
      a.setStatus("EXPIRED");
      actions.saveAndFlush(a);
      throw new ExpiredActionException();
    }
    if (a.getActionType().equals("WORKFLOW")) {
      var plan = workflows.parseStored(a.getPayload());
      var decisions = input == null || input.fieldDecisions() == null ? List.<AiWorkflowService.FieldDecision>of() : input.fieldDecisions();
      plan = workflows.resolveFieldDecisions(plan, decisions);
      var result = workflows.execute(plan);
      a.setStatus("EXECUTED");
      a.setConfirmedAt(Instant.now());
      actions.saveAndFlush(a);
      audit.log("AI_ACTION_CONFIRMED", "AiPendingAction", a.getId(), "AI");
      return result;
    }
    if (input != null && input.fieldDecisions() != null && !input.fieldDecisions().isEmpty())
      throw new ApiException(400, "INVALID_WORKFLOW_DECISION", "Field decisions are only accepted for workflow proposals.");
    Payload p;
    try {
      p = json.readValue(a.getPayload(), Payload.class);
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    Object result =
        switch (a.getActionType()) {
          case "ADMISSION" ->
              stays.create(
                  new AdmissionInput(
                      p.patientId(), p.doctorId(), p.roomId(), p.requiredRoomCapabilities()),
                  "AI");
          case "TRANSFER" ->
              stays.move(
                  p.admissionId(), new TransferInput(p.roomId(), p.reason(), p.version()), "AI");
          case "DISCHARGE" -> stays.close(p.admissionId(), p.version(), "AI");
          default -> throw new IllegalArgumentException();
        };
    a.setStatus("EXECUTED");
    a.setConfirmedAt(Instant.now());
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_CONFIRMED", "AiPendingAction", a.getId(), "AI");
    return result;
  }

  @Transactional
  public Object cancel(Long id) {
    lock.acquire();
    var a = get(id);
    if (!a.getStatus().equals("PENDING"))
      throw ApiException.conflict("ACTION_CONSUMED", "This action is no longer pending.");
    a.setStatus("CANCELLED");
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_CANCELLED", "AiPendingAction", a.getId(), "AI");
    return Views.pendingAction(a);
  }
}
