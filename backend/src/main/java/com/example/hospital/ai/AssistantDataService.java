package com.example.hospital.ai;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import com.example.hospital.service.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One place for a user to see and clear their own assistant data in the active department (#342):
 * sessions and stored conversation context, request metadata, proposals, uploaded files and patient
 * drafts. Audit events stay, because the audit trail is append-only.
 */
@Service
public class AssistantDataService {
  public static final String CONFIRMATION = "CLEAR ASSISTANT DATA";
  static final List<String> RETAINED = List.of(
      "Audit events about assistant use (for example AI_QUERY_EXECUTED and AI_ACTION_CONFIRMED) remain: the audit"
          + " trail is append-only and follows the organisation's retention policy. They hold no conversation text.",
      "Records created by confirmed proposals (patients, admissions, procedures) are hospital records and are not removed.");

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final AiSourceService sources;
  private final AiPatientDraftService drafts;

  public AssistantDataService(
      JdbcTemplate jdbc, Actor actor, AuditService audit, AiSourceService sources, AiPatientDraftService drafts) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.sources = sources;
    this.drafts = drafts;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export() {
    long user = actor.user().getId();
    long department = DepartmentContext.id();
    var result = new LinkedHashMap<String, Object>();
    result.put("departmentId", department);
    result.put("sessions", jdbc.queryForList(
        """
        select session_key as session_id, created_at, selected_patient_id,
               conversation_context is not null as context_stored, conversation_expires_at as context_expires_at,
               conversation_context as context
          from ai_sessions where user_id = ? and department_id = ? order by created_at desc
        """, user, department));
    result.put("interactions", jdbc.queryForList(
        """
        select session_id, started_at, completed_at, request_type, status, tool_names, model_identifier,
               latency_ms, prompt_tokens, completion_tokens
          from ai_interactions where user_id = ? and department_id = ? order by started_at desc limit 500
        """, user, department));
    result.put("proposals", jdbc.queryForList(
        """
        select id, action_type, status, created_at, expires_at, confirmed_at
          from ai_pending_actions where user_id = ? and department_id = ? order by created_at desc limit 500
        """, user, department));
    result.put("uploadedFiles", sources.summariesFor(user, department));
    result.put("patientDrafts", drafts.countFor(user, department));
    result.put("auditEventsRetained", jdbc.queryForObject(
        "select count(*) from audit_events where user_id = ? and department_id = ? and event_type like 'AI\\_%'",
        Long.class, user, department));
    result.put("retainedAfterClearing", RETAINED);
    return result;
  }

  /** Deletes the caller's assistant data in the active department; pending proposals can no longer be confirmed. */
  @Transactional
  public Map<String, Object> clear(String confirmation) {
    if (!CONFIRMATION.equals(confirmation))
      throw new ApiException(400, "CONFIRMATION_REQUIRED", "Type " + CONFIRMATION + " to delete your assistant data.");
    long user = actor.user().getId();
    long department = DepartmentContext.id();
    var removed = new LinkedHashMap<String, Object>();
    removed.put("pendingProposalsWithdrawn", jdbc.queryForObject(
        "select count(*) from ai_pending_actions where user_id = ? and department_id = ? and status = 'PENDING'",
        Long.class, user, department));
    removed.put("proposals", jdbc.update("delete from ai_pending_actions where user_id = ? and department_id = ?", user, department));
    removed.put("interactions", jdbc.update("delete from ai_interactions where user_id = ? and department_id = ?", user, department));
    removed.put("sessions", jdbc.update("delete from ai_sessions where user_id = ? and department_id = ?", user, department));
    removed.put("uploadedFiles", sources.removeAllFor(user, department));
    removed.put("patientDrafts", drafts.removeAllFor(user, department));
    audit.log("ASSISTANT_DATA_CLEARED", "AppUser", user, "UI", removed);
    var result = new LinkedHashMap<String, Object>(removed);
    result.put("retained", RETAINED);
    return result;
  }
}
