package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

class AppendOnlyAuditTest extends HospitalSupport {
  @Autowired TransactionTemplate transactions;
  private long eventId;

  @BeforeEach
  void recordAnEvent() throws Exception {
    long patientId = createPatient().get("id").asLong();
    eventId =
        jdbc.queryForObject(
            "select max(id) from audit_events where event_type = 'PATIENT_CREATED' and entity_id = ?",
            Long.class,
            patientId);
  }

  /** Runs inside a transaction that is always rolled back, so a broken guard cannot erase data. */
  private void attempt(String sql, Object... args) {
    transactions.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          jdbc.update(sql, args);
        });
  }

  @Test
  void priorEventsCannotBeChangedOrRemoved() {
    assertThatThrownBy(() -> attempt("update audit_events set event_type = 'TAMPERED' where id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("audit_events is append-only");
    assertThatThrownBy(() -> attempt("delete from audit_events where id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("audit_events is append-only");
    assertThatThrownBy(() -> attempt("truncate audit_events"))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("audit_events is append-only");
    assertThat(
            jdbc.queryForObject(
                "select event_type from audit_events where id = ?", String.class, eventId))
        .isEqualTo("PATIENT_CREATED");
  }

  @Test
  void onlyAnExplicitMaintenanceTransactionMayDeleteAndItDoesNotLeak() {
    transactions.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          jdbc.queryForObject(
              "select set_config('hospital.audit_maintenance', 'on', true)", String.class);
          assertThat(jdbc.update("delete from audit_events where id = ?", eventId)).isOne();
        });
    assertThat(jdbc.queryForObject("select count(*) from audit_events where id = ?", Long.class, eventId))
        .isOne();
    assertThatThrownBy(() -> attempt("delete from audit_events where id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("audit_events is append-only");
  }

  @Test
  void newEventsCanStillBeAppended() throws Exception {
    long before = jdbc.queryForObject("select count(*) from audit_events", Long.class);
    createPatient();
    assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isGreaterThan(before);
  }
}
