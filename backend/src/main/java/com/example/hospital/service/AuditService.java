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
    var e = new AuditEvent();
    e.userId = actor.user().id;
    e.eventType = event;
    e.entityType = entity;
    e.entityId = id;
    e.source = source;
    e.timestamp = Instant.now();
    e.metadata = "";
    events.save(e);
    publisher.publishEvent(new OperationsStream.Changed());
  }
}
