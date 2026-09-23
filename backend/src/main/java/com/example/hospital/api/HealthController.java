package com.example.hospital.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Public probes. Liveness only says the process can serve requests; readiness also requires the
 * database. Neither response includes connection details.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Kept for existing probes; equivalent to readiness. */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    return ready();
  }

  @GetMapping("/live")
  public Map<String, Object> live() {
    return Map.of("status", "UP", "service", "hospital-operations");
  }

  @GetMapping("/ready")
  public ResponseEntity<Map<String, Object>> ready() {
    var body = new LinkedHashMap<String, Object>();
    body.put("service", "hospital-operations");
    boolean database = databaseUp();
    body.put("database", database ? "UP" : "UNAVAILABLE");
    body.put("status", database ? "UP" : "DOWN");
    return ResponseEntity.status(database ? 200 : 503).body(body);
  }

  private boolean databaseUp() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return true;
    } catch (DataAccessException e) {
      return false;
    }
  }
}
