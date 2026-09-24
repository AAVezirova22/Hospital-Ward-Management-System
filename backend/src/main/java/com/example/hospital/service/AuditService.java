package com.example.hospital.service;

import com.example.hospital.domain.AuditEvent;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private static final Pattern SENSITIVE_REASON = Pattern.compile(
      "(?i)\\b(password|passcode|token|secret|credential|api[_ -]?key|private[_ -]?key)\\b\\s*[:=]\\s*\\S+|\\bbearer\\s+[A-Za-z0-9._~+/=-]{12,}|\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{8,}");
  /** Published for every audit row so other features can react after the change commits. */
  public record Recorded(
      long departmentId, Long userId, String event, String entity, Long entityId, String source) {}

  private final AuditEventRepository events;
  private final Actor actor;
  private final org.springframework.context.ApplicationEventPublisher publisher;

  public AuditService(AuditEventRepository events, Actor actor, org.springframework.context.ApplicationEventPublisher publisher) {
    this.events = events;
    this.actor = actor;
    this.publisher = publisher;
  }

  public void log(String event, String entity, Long id, String source) {
    log(event, entity, id, source, Map.of());
  }

  public void log(String event, String entity, Long id, String source, java.util.Map<String, ?> extra) {
    logInternal(null, event, entity, id, source, extra);
  }

  public void logForDepartment(long departmentId, String event, String entity, Long id, String source,
      java.util.Map<String, ?> extra) {
    logInternal(departmentId, event, entity, id, source, extra);
  }

  private void logInternal(Long targetDepartmentId, String event, String entity, Long id, String source,
      java.util.Map<String, ?> extra) {
    var e = new AuditEvent();
    e.setUserId(actor.user().getId());
    e.setEventType(event);
    e.setEntityType(entity);
    e.setEntityId(id);
    e.setSource(source);
    e.setTimestamp(Instant.now());
    var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("event", event);
    payload.put("entity", entity);
    if (id != null) payload.put("id", id);
    if (extra != null) extra.forEach((k, v) -> {
      if (v == null || k.toLowerCase().contains("password") || k.toLowerCase().contains("token")) return;
      if ("reason".equalsIgnoreCase(k)) {
        String reason = v.toString().strip();
        if (reason.length() > 300 || SENSITIVE_REASON.matcher(reason).find())
          throw new com.example.hospital.api.ApiException(400, "INVALID_AUDIT_REASON",
              "Reason must be brief and must not contain credentials or tokens.");
        if (!reason.isEmpty()) payload.put(k, reason);
      } else payload.put(k, v);
    });
    String json = payload.toString();
    if (json.length() > 500 && payload.containsKey("reason")) {
      String reason = String.valueOf(payload.get("reason"));
      int keep = Math.max(0, reason.length() - (json.length() - 500));
      while (keep >= 0) {
        payload.put("reason", reason.substring(0, keep));
        json = payload.toString();
        if (json.length() <= 500) break;
        keep -= json.length() - 500;
      }
    }
    e.setMetadata(json.length() > 500 ? json.substring(0, 500) : json);
    DepartmentContext.Scope previous = DepartmentContext.current();
    try {
      if (targetDepartmentId != null)
        DepartmentContext.set(new DepartmentContext.Scope(targetDepartmentId,
            previous == null ? "ADMIN" : previous.role(), previous == null ? null : previous.doctorId()));
      events.save(e);
    } finally {
      if (targetDepartmentId != null) {
        if (previous == null) DepartmentContext.clear(); else DepartmentContext.set(previous);
      }
    }
    publisher.publishEvent(
        new Recorded(
            e.getDepartmentId() == null ? -1L : e.getDepartmentId(), e.getUserId(), event, entity, id, source));
    publisher.publishEvent(new OperationsStream.Changed(
        targetDepartmentId == null ? DepartmentContext.id() : e.getDepartmentId()));
  }
}
