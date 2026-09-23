package com.example.hospital.api;

import com.example.hospital.service.NotificationPolicies;
import com.example.hospital.service.NotificationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Staff notification inbox, read state, acknowledgement and department policy (#303 #304 #310 #317 #320). */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService notifications;
  private final NotificationPolicies policies;

  public NotificationController(NotificationService notifications, NotificationPolicies policies) {
    this.notifications = notifications;
    this.policies = policies;
  }

  @GetMapping
  public NotificationService.Inbox inbox(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return notifications.inbox(page, size, category, status, unreadOnly);
  }

  @GetMapping("/counts")
  public NotificationService.Counts counts() {
    return notifications.counts();
  }

  @GetMapping("/{id}")
  public NotificationService.Notice notice(@PathVariable long id) {
    return notifications.get(id);
  }

  @PostMapping("/{id}/read")
  public NotificationService.Notice read(@PathVariable long id) {
    return notifications.markRead(id);
  }

  @DeleteMapping("/{id}/read")
  public NotificationService.Notice unread(@PathVariable long id) {
    return notifications.markUnread(id);
  }

  @PostMapping("/read-all")
  public Map<String, Object> readAll(@RequestParam(required = false) String category) {
    return notifications.markAllRead(category);
  }

  @PostMapping("/{id}/acknowledge")
  public NotificationService.Notice acknowledge(@PathVariable long id) {
    return notifications.acknowledge(id);
  }

  @GetMapping("/policy")
  public NotificationPolicies.Policy policy() {
    return policies.current();
  }

  @PutMapping("/policy")
  @PreAuthorize("hasRole('ADMIN')")
  public NotificationPolicies.Policy updatePolicy(@Valid @RequestBody NotificationPolicyInput in) {
    return policies.update(in);
  }
}
