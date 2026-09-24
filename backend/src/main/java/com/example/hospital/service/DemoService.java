package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {
  private final boolean enabled;
  private final Bootstrap bootstrap;
  private final JdbcTemplate jdbc;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final SecureRandom random = new SecureRandom();

  public DemoService(@Value("${app.demo:false}") boolean enabled, Bootstrap bootstrap,
      JdbcTemplate jdbc, WorkflowLockRepository lock, Actor actor) {
    this.enabled = enabled;
    this.bootstrap = bootstrap;
    this.jdbc = jdbc;
    this.lock = lock;
    this.actor = actor;
  }

  public boolean enabled() { return enabled; }

  public void requireDemo() {
    if (!enabled) throw new ApiException(404, "DEMO_DISABLED", "Demo access is disabled.");
  }

  @Transactional
  public void reset() {
    requireDemo();
    actor.admin();
    lock.acquireAll();
    // Dedicated synthetic database only. Clinical ids are not restarted, so stale
    // sessions cannot address newly seeded patients. Hospital and department 1 are
    // recreated so bootstrap enrollment stays valid. Audit history is append-only, so the reset
    // opts in to deleting it for this transaction only.
    jdbc.queryForObject("select set_config('hospital.audit_maintenance', 'on', true)", String.class);
    for (String table : new String[]{
        "email_outbox",
        "idempotency_keys",
        "calendar_feeds",
        "email_verifications",
        "ai_sessions",
        "ai_pending_actions",
        "ai_interactions",
        "audit_events", "access_reviews", "performed_procedures", "room_assignments", "admissions",
        "app_users", "patients", "doctors", "rooms", "medical_procedures",
        "department_memberships", "hospital_memberships", "departments", "hospitals"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.update("delete from workflow_lock where id not in (0, 1)");
    jdbc.update(
        "insert into hospitals(id, name, join_code) overriding system value values (1, 'Medcore Hospital', ?)",
        joinCode("H-"));
    jdbc.update(
        "insert into departments(id, hospital_id, name, join_code) overriding system value values (1, 1, 'General medicine', ?)",
        joinCode("D-"));
    jdbc.update("insert into workflow_lock(id) values (1) on conflict do nothing");
    bootstrap.run();
  }

  private String joinCode(String prefix) {
    byte[] bytes = new byte[12];
    random.nextBytes(bytes);
    return prefix + HexFormat.of().withUpperCase().formatHex(bytes);
  }
}
