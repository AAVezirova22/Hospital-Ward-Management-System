package com.example.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Feeds the security review queue after audited actions commit; never fails the original request. */
@Component
public class SecurityEventListener {
  private static final Logger log = LoggerFactory.getLogger(SecurityEventListener.class);

  private final SecurityEventService events;

  public SecurityEventListener(SecurityEventService events) {
    this.events = events;
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void audited(AuditService.Recorded event) {
    if (!SecurityEventService.tracked(event.event())) return;
    try {
      events.record(event);
    } catch (RuntimeException e) {
      log.warn("Security review entry for {} was not stored: {}", event.event(), e.getClass().getSimpleName());
    }
  }
}
