package com.example.hospital.service;

import com.example.hospital.domain.AuditEvent;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.security.Actor;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  /** Published for every audit row so other features can react after the change commits. */
  public record Recorded(
      long departmentId, Long userId, String event, String entity, Long entityId, String source) {}

  private final AuditEventRepository events;
  private final Actor actor;
  private final org.springframework.context.ApplicationEventPublisher publisher;
  private final boolean recordReads;
  private final Duration readDedupeWindow;

  public AuditService(
      AuditEventRepository events,
      Actor actor,
      org.springframework.context.ApplicationEventPublisher publisher,
      @Value("${app.audit.record-reads:true}") boolean recordReads,
      @Value("${app.audit.read-dedupe-window:15m}") Duration readDedupeWindow) {
    if (readDedupeWindow.isNegative())
      throw new IllegalStateException("AUDIT_READ_DEDUPE_WINDOW must not be negative.");
    this.events = events;
    this.actor = actor;
    this.publisher = publisher;
    this.recordReads = recordReads;
    this.readDedupeWindow = readDedupeWindow;
  }

  public void log(String event, String entity, Long id, String source) {
    log(event, entity, id, source, Map.of());
  }

  public void log(String event, String entity, Long id, String source, java.util.Map<String, ?> extra) {
    var e = event(actor.user().getId(), event, entity, id, source, Instant.now(), extra);
    events.save(e);
    publisher.publishEvent(
        new Recorded(
            e.getDepartmentId() == null ? -1L : e.getDepartmentId(), e.getUserId(), event, entity, id, source));
    publisher.publishEvent(new OperationsStream.Changed(com.example.hospital.security.DepartmentContext.id()));
  }

  /**
   * Records that the current user opened a patient-identifiable record: who, which record, which
   * department and when, never field values. A read changes nothing, so it publishes no event (no
   * notifications, no live refresh). Repeat views of the same record by the same user inside the
   * dedupe window are recorded once. Call outside read-only transactions.
   */
  public void read(String event, String entity, Long id, String source) {
    if (!recordReads || id == null) return;
    Long userId = actor.user().getId();
    Instant now = Instant.now();
    if (!readDedupeWindow.isZero()
        && events.existsByUserIdAndEventTypeAndEntityIdAndTimestampAfter(
            userId, event, id, now.minus(readDedupeWindow))) return;
    events.save(event(userId, event, entity, id, source, now, Map.of()));
  }

  private static AuditEvent event(
      Long userId, String event, String entity, Long id, String source, Instant at, Map<String, ?> extra) {
    var e = new AuditEvent();
    e.setUserId(userId);
    e.setEventType(event);
    e.setEntityType(entity);
    e.setEntityId(id);
    e.setSource(source);
    e.setTimestamp(at);
    var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("event", event);
    payload.put("entity", entity);
    if (id != null) payload.put("id", id);
    if (extra != null) extra.forEach((k, v) -> {
      if (v != null && !k.toLowerCase().contains("password") && !k.toLowerCase().contains("token"))
        payload.put(k, v);
    });
    String json = payload.toString();
    e.setMetadata(json.length() > 500 ? json.substring(0, 500) : json);
    return e;
  }
}
