package com.example.hospital.api;

import com.example.hospital.service.TaskReminderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated clinician push preferences and privacy-safe care-task reminder actions. */
@RestController
@RequestMapping("/api/v1/task-reminders")
public class TaskReminderController {
  private final TaskReminderService reminders;

  public TaskReminderController(TaskReminderService reminders) {
    this.reminders = reminders;
  }

  @GetMapping("/preferences")
  public TaskReminderService.Preferences preferences() {
    return reminders.preferences();
  }

  @PutMapping("/preferences")
  public TaskReminderService.Preferences updatePreferences(@Valid @RequestBody TaskReminderPreferencesInput input) {
    return reminders.updatePreferences(input);
  }

  @GetMapping("/vapid-public-key")
  public PublicKey vapidPublicKey() {
    return new PublicKey(reminders.vapidPublicKey());
  }

  @GetMapping("/subscriptions")
  public TaskReminderService.SubscriptionStatus subscriptions() {
    return reminders.subscriptions();
  }

  @PostMapping("/subscriptions")
  public TaskReminderService.SubscriptionStatus subscribe(@Valid @RequestBody PushSubscriptionInput input) {
    return reminders.subscribe(input);
  }

  @DeleteMapping("/subscriptions/{id}")
  public TaskReminderService.SubscriptionStatus revoke(@PathVariable long id) {
    return reminders.revokeSubscription(id);
  }

  @DeleteMapping("/subscriptions")
  public TaskReminderService.SubscriptionStatus revokeAll() {
    return reminders.revokeAllSubscriptions();
  }

  @GetMapping("/outcomes")
  public List<TaskReminderService.DeliveryOutcome> outcomes(@RequestParam(defaultValue = "50") int limit) {
    return reminders.outcomes(limit);
  }

  @GetMapping("/open/{token}")
  public TaskReminderService.OpenReminder open(@PathVariable UUID token) {
    return reminders.open(token);
  }

  @PostMapping("/snooze/{token}")
  public TaskReminderService.SnoozeResult snooze(@PathVariable UUID token, @RequestBody(required = false) SnoozeInput input) {
    int minutes = input == null || input.minutes() == null ? 15 : input.minutes();
    return reminders.snooze(token, minutes);
  }

  public record PublicKey(String publicKey) {}
  public record SnoozeInput(Integer minutes) {}
}
