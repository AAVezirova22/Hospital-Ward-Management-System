package com.example.hospital.api;

import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.service.DemoService;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
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
  public DemoController(DemoService demo, AppUserRepository users) {
    this.demo = demo;
    this.users = users;
  }

  @GetMapping("/status")
  public Object status() { return Map.of("enabled", demo.enabled()); }

  public record DemoLogin(String role) {}

  @PostMapping("/login")
  public Object login(@RequestBody DemoLogin input, HttpServletRequest request, HttpServletResponse response) {
    demo.requireDemo();
    String username = switch (Objects.toString(input.role(), "")) {
      case "ADMIN" -> "admin";
      case "DOCTOR" -> "doctor";
      case "MEDICAL_STAFF" -> "staff";
      default -> throw new ApiException(400, "INVALID_ROLE", "Choose a demonstration role.");
    };
    var user = users.findByUsername(username).orElseThrow(ApiException::missing);
    if (!user.enabled || !user.role.equals(input.role()))
      throw new ApiException(403, "DEMO_ACCOUNT_UNAVAILABLE", "This demo account is unavailable.");
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(user.username, null,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.role))));
    SecurityContextHolder.setContext(context);
    request.getSession(true).setAttribute("credentialStamp", user.passwordHash);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    user.lastLoginAt = Instant.now();
    users.save(user);
    return user;
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
}
