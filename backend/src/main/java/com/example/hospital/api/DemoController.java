package com.example.hospital.api;

import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.security.SessionStamps;
import com.example.hospital.service.DemoService;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
  private final DemoService demo;
  private final AppUserRepository users;
  private final String demoToken;
  private final String environment;

  public DemoController(
      DemoService demo,
      AppUserRepository users,
      @Value("${app.demo-token:}") String demoToken,
      @Value("${app.environment:development}") String environment) {
    this.demo = demo;
    this.users = users;
    this.demoToken = demoToken;
    this.environment = environment;
  }

  @GetMapping("/status")
  public Object status() { return Map.of("enabled", demo.enabled(), "environment", environment); }

  public record DemoLogin(String role) {}

  @PostMapping("/login")
  public Object login(@RequestBody DemoLogin input, HttpServletRequest request, HttpServletResponse response) {
    demo.requireDemo();
    authorizeDemoNetwork(request);
    requireSeedIdentities();
    String username = switch (Objects.toString(input.role(), "")) {
      case "ADMIN" -> "admin";
      case "DOCTOR" -> "doctor";
      case "MEDICAL_STAFF" -> "staff";
      default -> throw new ApiException(400, "INVALID_ROLE", "Choose a demonstration role.");
    };
    var user = users.findByUsername(username).orElseThrow(ApiException::missing);
    if (!user.isEnabled() || !user.getRole().equals(input.role()))
      throw new ApiException(403, "DEMO_ACCOUNT_UNAVAILABLE", "This demo account is unavailable.");
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
    SecurityContextHolder.setContext(context);
    if (user.getSessionStamp() == null || user.getSessionStamp().isBlank()) user.setSessionStamp(SessionStamps.next());
    request.getSession(true).setAttribute("credentialStamp", user.getSessionStamp());
    com.example.hospital.security.SessionLifetime.markAuthenticated(request.getSession());
    request.getSession().setAttribute("accountId", user.getId());
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    user.setLastLoginAt(Instant.now());
    users.save(user);
    return Views.account(user);
  }

  @PostMapping("/reset")
  public Object reset(@RequestBody Map<String, String> input, HttpServletRequest request) {
    if (!"RESET DEMO".equals(input.get("confirmation")))
      throw new ApiException(400, "CONFIRMATION_REQUIRED", "Type RESET DEMO to restore the scenario.");
    demo.reset();
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status", "RESET", "message", "Demonstration restored. Sign in again.");
  }

  private void authorizeDemoNetwork(HttpServletRequest request) {
    String provided = request.getHeader("X-Demo-Token");
    if (!demoToken.isBlank()) {
      if (provided == null
          || !MessageDigest.isEqual(
              demoToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
        throw new ApiException(403, "DEMO_TOKEN_REQUIRED", "Demo login requires a valid demonstration token.");
      return;
    }
    String ip = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    if (!(ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("https://example.net/id/garnet") || ip.startsWith("0:")))
      throw new ApiException(403, "DEMO_TOKEN_REQUIRED", "Demo login is limited to loopback unless DEMO_TOKEN is set.");
  }

  private void requireSeedIdentities() {
    for (var expected : List.of(Map.entry("admin", "ADMIN"), Map.entry("doctor", "DOCTOR"), Map.entry("staff", "MEDICAL_STAFF"))) {
      var found = users.findByUsername(expected.getKey());
      if (found.isEmpty() || !expected.getValue().equals(found.get().getRole()) || !found.get().isEnabled())
        throw new ApiException(403, "DEMO_ACCOUNT_UNAVAILABLE", "This demo account is unavailable.");
    }
  }
}
