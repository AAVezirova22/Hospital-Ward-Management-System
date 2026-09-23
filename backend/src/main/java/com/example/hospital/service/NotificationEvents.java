package com.example.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Feeds the notification inbox. Listeners run after the audited change commits (or immediately when
 * the change ran without a transaction) and never fail the request that triggered them. The sweep
 * catches anything the listeners missed and runs escalation and retention on a timer.
 *
 * <p>The work happens in {@link NotificationService} so every call goes through the Spring proxy and
 * gets its own transaction.
 */
@Component
public class NotificationEvents {
  private static final Logger log = LoggerFactory.getLogger(NotificationEvents.class);

  private final NotificationService notifications;
  private final boolean sweepEnabled;

  public NotificationEvents(
      NotificationService notifications,
      @Value("${app.notifications.sweep-enabled:true}") boolean sweepEnabled) {
    this.notifications = notifications;
    this.sweepEnabled = sweepEnabled;
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void audited(AuditService.Recorded event) {
    try {
      notifications.recordActivity(event);
    } catch (RuntimeException e) {
      log.warn("Recent-action notice for {} was not stored: {}", event.event(), e.getClass().getSimpleName());
    }
    if (!NotificationService.CAPACITY_EVENTS.contains(event.event())) return;
    try {
      notifications.evaluateCapacity(event.departmentId());
    } catch (RuntimeException e) {
      log.warn(
          "Capacity alerts for department {} were not refreshed: {}",
          event.departmentId(),
          e.getClass().getSimpleName());
    }
  }

  @Scheduled(
      initialDelayString = "${app.notifications.sweep-initial-delay-ms:30000}",
      fixedDelayString = "${app.notifications.sweep-interval-ms:60000}")
  public void sweep() {
    if (!sweepEnabled) return;
    for (long departmentId : notifications.departmentIds()) {
      try {
        notifications.evaluateCapacity(departmentId);
        notifications.escalateDue(departmentId);
      } catch (RuntimeException e) {
        log.warn("Notification sweep failed for department {}: {}", departmentId, e.getClass().getSimpleName());
      }
    }
    try {
      notifications.purgeExpired();
    } catch (RuntimeException e) {
      log.warn("Expired notifications were not purged: {}", e.getClass().getSimpleName());
    }
  }
}
