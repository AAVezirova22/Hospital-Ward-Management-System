package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
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
    lock.acquire();
    // Only enabled on a dedicated synthetic database. IDs are never reused:
    // old sessions and stale proposals cannot refer to newly created records.
    for (String table : new String[]{"email_verifications", "ai_sessions", "ai_pending_actions", "ai_interactions",
        "audit_events", "performed_procedures", "room_assignments", "admissions",
        "app_users", "patients", "doctors", "rooms", "medical_procedures"}) {
      jdbc.update("delete from " + table);
    }
    bootstrap.run();
  }
}
