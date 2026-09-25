package com.example.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.task-reminders.enabled", havingValue = "true")
public class TaskReminderScheduler {
  private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);
  private final TaskReminderService reminders;

  public TaskReminderScheduler(TaskReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(fixedDelayString = "${app.task-reminders.poll-interval-ms:60000}")
  public void processDueReminders() {
    try {
      reminders.reconcileAndDeliverDue();
    } catch (RuntimeException e) {
      log.error("Care task reminder polling failed; the next scheduled poll will retry.", e);
    }
  }
}
