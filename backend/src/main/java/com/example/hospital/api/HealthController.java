package com.example.hospital.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Public probes. Liveness only says the process can serve requests; readiness also requires the
 * database and a fully applied schema. Neither response includes connection details or versions.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
  private final JdbcTemplate jdbc;
  private final ObjectProvider<Flyway> flyway;
  // A schema that was fully applied stays applied for this process, so skip rescanning.
  private volatile boolean schemaApplied;

  public HealthController(JdbcTemplate jdbc, ObjectProvider<Flyway> flyway) {
    this.jdbc = jdbc;
    this.flyway = flyway;
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
    boolean migrations = database && migrationsApplied();
    body.put("migrations", !database ? "UNKNOWN" : migrations ? "UP" : "PENDING");
    boolean ready = database && migrations;
    body.put("status", ready ? "UP" : "DOWN");
    return ResponseEntity.status(ready ? 200 : 503).body(body);
  }

  private boolean databaseUp() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return true;
    } catch (DataAccessException e) {
      return false;
    }
  }

  /** True when no migration is pending or failed; deployments without Flyway count as applied. */
  private boolean migrationsApplied() {
    if (schemaApplied) return true;
    Flyway f = flyway.getIfAvailable();
    if (f == null) return true;
    try {
      for (var m : f.info().all()) {
        MigrationState state = m.getState();
        if (state == MigrationState.PENDING || state.isFailed()) return false;
      }
      schemaApplied = true;
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
