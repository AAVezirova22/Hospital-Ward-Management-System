package com.example.hospital.ai;

import com.example.hospital.api.*;
import com.example.hospital.api.Inputs.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiActionService {
  private final AiPendingActionRepository actions;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final HospitalService h;
  private final AuditService audit;
  private final ObjectMapper json;
  private final int ttl;
  private final AiWorkflowService workflows;

  public AiActionService(
      AiPendingActionRepository a,
      WorkflowLockRepository l,
      Actor actor,
      HospitalService h,
      AuditService au,
      ObjectMapper j,
      @Value("${app.ai.action-ttl-seconds}") int ttl,
      AiWorkflowService workflows) {
    actions = a;
    lock = l;
    this.actor = actor;
    this.h = h;
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
      Long patientId, Long admissionId, Long roomId, Long doctorId, Long version, String reason) {}

  public AiPendingAction get(Long id) {
    var a = actions.findById(id).orElseThrow(ApiException::missing);
    if (!a.userId.equals(actor.user().id))
      throw new AccessDeniedException("Action belongs to another user");
    return a;
  }

  @Transactional
  public Object prepare(String type, Payload p) {
    actor.staff();
    var a = new AiPendingAction();
    a.userId = actor.user().id;
    a.actionType = type;
    a.expiresAt = Instant.now().plusSeconds(ttl);
    try {
      a.payload = json.writeValueAsString(p);
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_PREPARED", "AiPendingAction", a.id, "AI");
    return card(a, p);
  }

  public Object card(AiPendingAction a, Payload p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("action", a);
    m.put("patient", h.patient(p.patientId()));
    if (p.roomId() != null) m.put("destination", h.room(p.roomId()));
    if (p.admissionId() != null) m.put("current", h.admissionView(h.admission(p.admissionId())));
    if (p.doctorId() != null)
      m.put(
          "doctor",
          h.doctors().stream().filter(d -> d.id.equals(p.doctorId())).findFirst().orElseThrow());
    return m;
  }

  @Transactional
  public Object prepareWorkflow(String text) {
    var plan = workflows.parse(text);
    var a = new AiPendingAction();
    a.userId = actor.user().id;
    a.actionType = "WORKFLOW";
    a.expiresAt = Instant.now().plusSeconds(ttl);
    try { a.payload = json.writeValueAsString(plan); }
    catch (Exception e) { throw new IllegalArgumentException(); }
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_PREPARED", "AiPendingAction", a.id, "AI");
    return Map.of("action", a, "workflow", plan);
  }

  @Transactional(noRollbackFor = ExpiredActionException.class)
  public Object confirm(Long id) {
    lock.acquire();
    actor.staff();
    var a = get(id);
    if (!a.status.equals("PENDING"))
      throw ApiException.conflict("ACTION_CONSUMED", "This action is no longer pending.");
    if (Instant.now().isAfter(a.expiresAt)) {
      a.status = "EXPIRED";
      actions.saveAndFlush(a);
      throw new ExpiredActionException();
    }
    if (a.actionType.equals("WORKFLOW")) {
      var result = workflows.execute(workflows.parse(a.payload));
      a.status = "EXECUTED";
      a.confirmedAt = Instant.now();
      actions.saveAndFlush(a);
      audit.log("AI_ACTION_CONFIRMED", "AiPendingAction", a.id, "AI");
      return result;
    }
    Payload p;
    try {
      p = json.readValue(a.payload, Payload.class);
    } catch (Exception e) {
      throw new IllegalArgumentException();
    }
    Object result =
        switch (a.actionType) {
          case "ADMISSION" ->
              h.admit(new AdmissionInput(p.patientId(), p.doctorId(), p.roomId()), "AI");
          case "TRANSFER" ->
              h.transfer(
                  p.admissionId(), new TransferInput(p.roomId(), p.reason(), p.version()), "AI");
          case "DISCHARGE" -> h.discharge(p.admissionId(), p.version(), "AI");
          default -> throw new IllegalArgumentException();
        };
    a.status = "EXECUTED";
    a.confirmedAt = Instant.now();
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_CONFIRMED", "AiPendingAction", a.id, "AI");
    return result;
  }

  @Transactional
  public Object cancel(Long id) {
    lock.acquire();
    var a = get(id);
    if (!a.status.equals("PENDING"))
      throw ApiException.conflict("ACTION_CONSUMED", "This action is no longer pending.");
    a.status = "CANCELLED";
    actions.saveAndFlush(a);
    audit.log("AI_ACTION_CANCELLED", "AiPendingAction", a.id, "AI");
    return a;
  }
}
