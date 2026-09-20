package com.example.hospital.service;

import com.example.hospital.domain.AuditEvent;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.security.Actor;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
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
    var e = new AuditEvent();
    e.userId = actor.user().id;
    e.eventType = event;
    e.entityType = entity;
    e.entityId = id;
    e.source = source;
    e.timestamp = Instant.now();
    var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("event", event);
    payload.put("entity", entity);
    if (id != null) payload.put("id", id);
    if (extra != null) extra.forEach((k, v) -> {
      if (v != null && !k.toLowerCase().contains("password") && !k.toLowerCase().contains("token"))
        payload.put(k, v);
    });
    String json = payload.toString();
    e.metadata = json.length() > 500 ? json.substring(0, 500) : json;
    events.save(e);
    publisher.publishEvent(new OperationsStream.Changed(com.example.hospital.security.DepartmentContext.id()));
  }
}
