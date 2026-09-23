package com.example.hospital.api;

import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.security.SessionStamps;
import com.example.hospital.service.DemoService;
import jakarta.servlet.http.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
  private final boolean publicLogin;

  public DemoController(
      DemoService demo,
      AppUserRepository users,
      @Value("${app.demo-token:}") String demoToken,
      @Value("${app.demo-public-login}") boolean publicLogin) {
    this.demo = demo;
    this.users = users;
    this.demoToken = demoToken;
    this.publicLogin = publicLogin;
  }

  @GetMapping("/status")
  public Object status() { return Map.of("enabled", demo.enabled()); }

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
    if (publicLogin) return;
    if (!isLoopbackAddress(request.getRemoteAddr()))
      throw new ApiException(
          403,
          "DEMO_TOKEN_REQUIRED",
          "Demo login is limited to loopback unless DEMO_TOKEN or DEMO_PUBLIC_LOGIN is set.");
  }

  private static boolean isLoopbackAddress(String address) {
    if (address == null) return false;
    if (!address.contains(":") && !address.matches("[0-9.]+")) return false;
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException ignored) {
      return false;
    }
  }

  private void requireSeedIdentities() {
    for (var expected : List.of(Map.entry("admin", "ADMIN"), Map.entry("doctor", "DOCTOR"), Map.entry("staff", "MEDICAL_STAFF"))) {
      var found = users.findByUsername(expected.getKey());
      if (found.isEmpty() || !expected.getValue().equals(found.get().getRole()) || !found.get().isEnabled())
        throw new ApiException(403, "DEMO_ACCOUNT_UNAVAILABLE", "This demo account is unavailable.");
    }
  }
}
