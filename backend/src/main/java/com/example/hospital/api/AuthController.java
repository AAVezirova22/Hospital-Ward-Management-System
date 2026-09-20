package com.example.hospital.api;

import com.example.hospital.security.Actor;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final Actor actor;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public AuthController(Actor actor, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.actor = actor;
    this.jdbc = jdbc;
  }

  @GetMapping("/health")
  public org.springframework.http.ResponseEntity<?> health() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return org.springframework.http.ResponseEntity.ok(Map.of("status", "UP", "database", "UP", "service", "hospital-operations"));
    } catch (org.springframework.dao.DataAccessException e) {
      return org.springframework.http.ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "UNAVAILABLE"));
    }
  }

  @GetMapping("/auth/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/auth/me")
  public Object me() {
    return actor.user();
  }
}
