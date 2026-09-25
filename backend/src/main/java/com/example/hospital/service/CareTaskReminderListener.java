package com.example.hospital.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CareTaskReminderListener {
  private final TaskReminderService reminders;

  public CareTaskReminderListener(TaskReminderService reminders) {
    this.reminders = reminders;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskChanged(CareTaskChanged event) {
    reminders.taskChanged(event.taskId());
  }
}
