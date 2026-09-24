package com.example.hospital.api;

import com.example.hospital.ai.AiProviderCheck;
import com.example.hospital.security.Actor;
import com.example.hospital.service.AuditService;
import com.example.hospital.service.RateLimitService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AiSettingsController {
  private final AiProviderCheck check;
  private final RateLimitService rates;
  private final AuditService audit;
  private final Actor actor;

  public AiSettingsController(AiProviderCheck check, RateLimitService rates, AuditService audit, Actor actor) {
    this.check = check;
    this.rates = rates;
    this.audit = audit;
    this.actor = actor;
  }

  @GetMapping
  public Map<String, Object> configuration() {
    return check.configuration();
  }

  @PostMapping("/test")
  public Map<String, Object> test() {
    rates.hit("ai-provider-test:" + actor.user().getId(), 5, Duration.ofMinutes(10), "RATE_LIMITED",
        "Wait a few minutes before testing the provider again.");
    var result = check.run();
    var details = new LinkedHashMap<String, Object>();
    details.put("outcome", result.get("outcome"));
    details.put("providerStatus", result.get("providerStatus"));
    details.put("providerHost", result.get("providerHost"));
    audit.log("AI_PROVIDER_TESTED", "Settings", null, "UI", details);
    return result;
  }
}
