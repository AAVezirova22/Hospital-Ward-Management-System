package com.example.hospital.api;

import com.example.hospital.security.Actor;
import com.example.hospital.service.AuditService;
import com.example.hospital.service.ConfirmationEmailService;
import com.example.hospital.service.RateLimitService;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Administrator view of email delivery settings (#393): what is configured, whether the sender
 * and public URL look valid, and a rate-limited test send. Keys and provider response text are
 * never returned.
 */
@RestController
@RequestMapping("/api/v1/settings/email")
@PreAuthorize("hasRole('ADMIN')")
public class EmailSettingsController {
  private static final String EMAIL = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

  private final ConfirmationEmailService email;
  private final RateLimitService rates;
  private final AuditService audit;
  private final Actor actor;
  private final AtomicReference<Map<String, Object>> lastTest = new AtomicReference<>();

  public EmailSettingsController(
      ConfirmationEmailService email, RateLimitService rates, AuditService audit, Actor actor) {
    this.email = email;
    this.rates = rates;
    this.audit = audit;
    this.actor = actor;
  }

  @GetMapping
  public Map<String, Object> status() {
    var status = new LinkedHashMap<String, Object>();
    String sender = email.sender();
    status.put("apiKeyConfigured", email.keyPresent());
    status.put("sender", sender.isBlank() ? null : sender);
    status.put("senderValid", !sender.isBlank() && email.sendTestSenderValid());
    status.put("testingSender", sender.contains("@resend.dev"));
    status.put("publicUrlConfigured", !email.publicUrl().isBlank());
    status.put("publicUrlValid", validPublicUrl(email.publicUrl()));
    status.put("providerHost", email.endpointHost());
    status.put("registrationEmailsReady", email.configured() && validPublicUrl(email.publicUrl()));
    status.put("reminderEmailsReady", email.remindersConfigured());
    status.put("lastTest", lastTest.get());
    return status;
  }

  @PostMapping("/test")
  public Map<String, Object> test(@RequestBody(required = false) Map<String, String> input) {
    String requested = input == null ? null : input.get("recipient");
    String recipient = requested == null || requested.isBlank() ? ownVerifiedEmail() : requested.strip();
    if (recipient == null)
      throw new ApiException(400, "RECIPIENT_REQUIRED", "Enter a recipient or verify your account email first.");
    if (recipient.length() > 254 || !recipient.matches(EMAIL))
      throw new ApiException(400, "INVALID_RECIPIENT", "Enter a valid email address.");
    rates.hit("email-settings-test:" + actor.user().getId(), 5, Duration.ofMinutes(10), "RATE_LIMITED",
        "Wait a few minutes before sending another test email.");
    var outcome = email.sendTest(recipient);
    var result = new LinkedHashMap<String, Object>();
    result.put("outcome", outcome.outcome());
    result.put("providerStatus", outcome.providerStatus());
    result.put("providerMessageId", outcome.providerMessageId());
    result.put("recipientDomain", recipient.substring(recipient.indexOf('@') + 1));
    result.put("checkedAt", Instant.now());
    lastTest.set(result);
    var details = new LinkedHashMap<String, Object>(result);
    details.remove("checkedAt");
    details.remove("providerMessageId");
    audit.log("EMAIL_SETTINGS_TESTED", "Settings", null, "UI", details);
    return result;
  }

  private String ownVerifiedEmail() {
    var user = actor.user();
    return user.getEmail() != null && user.isEmailVerified() ? user.getEmail() : null;
  }

  private static boolean validPublicUrl(String value) {
    if (value == null || value.isBlank()) return false;
    try {
      URI uri = URI.create(value);
      return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
          && uri.getHost() != null && uri.getRawQuery() == null && uri.getRawFragment() == null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
