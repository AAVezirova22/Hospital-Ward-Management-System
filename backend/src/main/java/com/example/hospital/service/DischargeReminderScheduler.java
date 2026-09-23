package com.example.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.discharge-reminders.enabled", havingValue = "true")
public class DischargeReminderScheduler {
  private static final Logger log = LoggerFactory.getLogger(DischargeReminderScheduler.class);
  private final DischargeReminderService reminders;

  public DischargeReminderScheduler(DischargeReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(fixedDelayString = "${app.discharge-reminders.poll-interval-ms:300000}")
  public void processDueReminders() {
    try {
      reminders.processDueReminders();
    } catch (RuntimeException e) {
      log.error("Discharge reminder polling failed; the next scheduled poll will retry.", e);
    }
  }
}
