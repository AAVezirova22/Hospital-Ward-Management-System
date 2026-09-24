package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.CareWorkflowInput;
import com.example.hospital.api.CareWorkflowInput.Task;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Department-scoped, clinician-authored task templates. Definitions contain descriptive tasks only. */
@Service
public class CareWorkflowService {
  private static final Set<String> TRIGGERS = Set.of("MANUAL", "ADMISSION", "DISCHARGE");
  private static final Set<String> OWNER_ROLES = Set.of("ADMIN", "MEDICAL_STAFF", "DOCTOR");
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final ApplicationEventPublisher publisher;

  public record LaunchInput(
      @jakarta.validation.constraints.NotNull Long workflowVersion,
      @jakarta.validation.constraints.NotNull Long patientId,
      Long admissionId,
      String idempotencyKey,
      String sourceReference,
      @jakarta.validation.constraints.Size(max = 2000) String patientSummary,
      @jakarta.validation.constraints.AssertTrue boolean approved) {}

  public record PreviewInput(Long patientId, Long admissionId, String trigger, Long workflowVersion) {}
  public record CancelInput(@jakarta.validation.constraints.NotNull Long version) {}
  public record TaskUpdate(
      @jakarta.validation.constraints.NotBlank String status,
      Long assignedUserId,
      @jakarta.validation.constraints.NotNull Long version) {}
  public record ConsentInput(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String consentType,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String consentVersion,
      @jakarta.validation.constraints.AssertTrue boolean confirmed) {}

  public CareWorkflowService(JdbcTemplate jdbc, Actor actor, AuditService audit,
      ObjectMapper mapper, ApplicationEventPublisher publisher) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.mapper = mapper;
    this.publisher = publisher;
  }

  public List<Map<String, Object>> list() {
    long departmentId = department();
    clinician();
    return jdbc.queryForList("""
        select id, name, description, version, published_version, created_by, created_at, updated_at
          from care_workflow_templates where department_id=? order by updated_at desc, id desc
        """, departmentId);
  }

  public Map<String, Object> get(long id) {
    long departmentId = department();
    clinician();
    var rows = jdbc.queryForList("""
        select id, name, description, draft_definition, version, published_version, created_by, created_at, updated_at
          from care_workflow_templates where department_id=? and id=?
        """, departmentId, id);
    if (rows.isEmpty()) throw ApiException.missing();
    var result = new LinkedHashMap<String, Object>(rows.getFirst());
    result.put("draft", definition((String) result.remove("draft_definition")));
    result.put("publishedVersions", jdbc.queryForList("""
        select id, version_number as version, definition, published_by, published_at
          from care_workflow_versions where department_id=? and template_id=? order by version_number desc
        """, departmentId, id).stream().map(row -> {
          var version = new LinkedHashMap<String, Object>(row);
          version.put("definition", definition((String) version.get("definition")));
          return version;
        }).toList());
    return result;
  }

  @Transactional
  public Map<String, Object> create(CareWorkflowInput in) {
    clinician();
    if (in.version() != 0) throw ApiException.conflict("STALE_STATE", "A new draft must start at version 0.");
    var normalized = normalize(in);
    long departmentId = department();
    long id = jdbc.queryForObject("""
        insert into care_workflow_templates(department_id,name,description,draft_definition,created_by)
        values (?,?,?,?,?) returning id
        """, Long.class, departmentId, in.name().trim(), clean(in.description()), json(normalized), actor.user().getId());
    audit.log("CARE_WORKFLOW_DRAFT_CREATED", "CareWorkflowTemplate", id, "UI");
    return get(id);
  }

  @Transactional
  public Map<String, Object> update(long id, CareWorkflowInput in) {
    clinician();
    var normalized = normalize(in);
    long departmentId = department();
    int changed = jdbc.update("""
        update care_workflow_templates set name=?, description=?, draft_definition=?, version=version+1, updated_at=now()
         where department_id=? and id=? and version=?
        """, in.name().trim(), clean(in.description()), json(normalized), departmentId, id, in.version());
    if (changed != 1) {
      if (!existsTemplate(id, departmentId)) throw ApiException.missing();
      throw ApiException.conflict("STALE_STATE", "This draft changed. Refresh before continuing.");
    }
    audit.log("CARE_WORKFLOW_DRAFT_UPDATED", "CareWorkflowTemplate", id, "UI");
    return get(id);
  }

  public Map<String, Object> preview(long id, PreviewInput in) {
    clinician();
    var template = template(id);
    var definition = definition((String) template.get("draft_definition"));
    Target target = target(in.patientId(), in.admissionId(), false);
    String trigger = normalizeTrigger(in.trigger() == null ? "MANUAL" : in.trigger());
    requireTrigger(definition, trigger);
    return previewView(id, null, target, trigger, definition, Instant.now());
  }

  @Transactional
  public Map<String, Object> publish(long id, Long expectedVersion) {
    clinician();
    long departmentId = department();
    var template = template(id);
    long actual = ((Number) template.get("version")).longValue();
    if (expectedVersion == null || expectedVersion != actual)
      throw ApiException.conflict("STALE_STATE", "This draft changed. Refresh before publishing.");
    var definition = definition((String) template.get("draft_definition"));
    validateDecoded(definition);
    Integer latest = jdbc.queryForObject("select coalesce(max(version_number),0) from care_workflow_versions where department_id=? and template_id=?", Integer.class, departmentId, id);
    int number = latest + 1;
    Long versionId = jdbc.queryForObject("""
        insert into care_workflow_versions(department_id,template_id,version_number,definition,published_by)
        values (?,?,?,?,?) returning id
        """, Long.class, departmentId, id, number, json(definition), actor.user().getId());
    int changed = jdbc.update("update care_workflow_templates set published_version=?, version=version+1, updated_at=now() where department_id=? and id=? and version=?", number, departmentId, id, expectedVersion);
    if (changed != 1) throw ApiException.conflict("STALE_STATE", "This draft changed. Refresh before publishing.");
    audit.log("CARE_WORKFLOW_VERSION_PUBLISHED", "CareWorkflowTemplate", id, "UI", Map.of("workflowVersion", number));
    return Map.of("templateId", id, "workflowVersionId", versionId, "version", number, "definition", definition);
  }

  public Map<String, Object> previewPublished(long id, long versionNumber, PreviewInput in) {
    clinician();
    var version = version(id, versionNumber);
    var definition = definition((String) version.get("definition"));
    Target target = target(in.patientId(), in.admissionId(), false);
    String trigger = normalizeTrigger(in.trigger() == null ? "MANUAL" : in.trigger());
    requireTrigger(definition, trigger);
    return previewView(id, versionNumber, target, trigger, definition, Instant.now());
  }

  @Transactional
  public Map<String, Object> launch(long templateId, LaunchInput in) {
    clinician();
    if (!in.approved()) throw new ApiException(400, "REVIEW_REQUIRED", "Clinician approval is required before launch.");
    String sourceKey = in.idempotencyKey() == null || in.idempotencyKey().isBlank()
        ? "manual:" + java.util.UUID.randomUUID() : in.idempotencyKey().trim();
    if (sourceKey.length() > 120) throw invalid("Idempotency key is too long.");
    var version = versionById(in.workflowVersion(), templateId);
    var definition = definition((String) version.get("definition"));
    requireTrigger(definition, "MANUAL");
    Target target = target(in.patientId(), in.admissionId(), true);
    if (in.patientSummary() != null && !in.patientSummary().isBlank()) requirePortalSummaryConsent(target.patientId());
    return launchDefinition(templateId, ((Number) version.get("id")).longValue(),
        ((Number) version.get("version_number")).intValue(), definition, target, "MANUAL", sourceKey,
        in.sourceReference(), in.patientSummary(), actor.user().getId());
  }

  public List<Map<String, Object>> consents() {
    clinicianOrPatient();
    long patientId = portalPatient();
    return jdbc.queryForList("""
        select id, consent_type as consentType, consent_version as consentVersion, recorded_at as recordedAt,
          withdrawn_at as withdrawnAt from patient_consents where department_id=? and patient_id=? order by recorded_at desc, id desc
        """, department(), patientId);
  }

  @Transactional
  public Map<String, Object> recordConsent(ConsentInput in) {
    clinicianOrPatient();
    if (!in.confirmed()) throw new ApiException(400, "CONSENT_CONFIRMATION_REQUIRED", "Record consent only after the patient has explicitly agreed.");
    String type = in.consentType().trim().toUpperCase(java.util.Locale.ROOT);
    if (!Set.of("PORTAL_FOLLOW_UP_SUMMARY", "PORTAL_ACCESS", "CARE_COMMUNICATION").contains(type)) throw invalid("Choose a supported consent type.");
    long patientId = portalPatient();
    long departmentId = department();
    Boolean active = jdbc.queryForObject("select count(*)>0 from patient_consents where department_id=? and patient_id=? and consent_type=? and withdrawn_at is null", Boolean.class, departmentId, patientId, type);
    if (Boolean.TRUE.equals(active)) throw ApiException.conflict("CONSENT_ALREADY_ACTIVE", "This consent is already active.");
    Long id = jdbc.queryForObject("""
        insert into patient_consents(department_id,patient_id,consent_type,consent_version,recorded_by)
        values (?,?,?,?,?) returning id
        """, Long.class, departmentId, patientId, type, in.consentVersion().trim(), actor.user().getId());
    audit.log("PATIENT_CONSENT_RECORDED", "PatientConsent", id, "UI", Map.of("patientId", patientId, "consentType", type, "consentVersion", in.consentVersion().trim()));
    return jdbc.queryForMap("select id, consent_type as consentType, consent_version as consentVersion, recorded_at as recordedAt, withdrawn_at as withdrawnAt from patient_consents where department_id=? and patient_id=? and id=?", departmentId, patientId, id);
  }

  @Transactional
  public Map<String, Object> withdrawConsent(long consentId, boolean confirmed) {
    clinicianOrPatient();
    if (!confirmed) throw new ApiException(400, "CONSENT_CONFIRMATION_REQUIRED", "Withdraw consent only after the patient has explicitly requested it.");
    long departmentId = department(); long patientId = portalPatient();
    int changed = jdbc.update("update patient_consents set withdrawn_at=now(),withdrawn_by=?,updated_at=now() where department_id=? and patient_id=? and id=? and withdrawn_at is null", actor.user().getId(), departmentId, patientId, consentId);
    if (changed != 1) throw ApiException.missing();
    audit.log("PATIENT_CONSENT_WITHDRAWN", "PatientConsent", consentId, "UI", Map.of("patientId", patientId));
    return jdbc.queryForMap("select id, consent_type as consentType, consent_version as consentVersion, recorded_at as recordedAt, withdrawn_at as withdrawnAt from patient_consents where department_id=? and patient_id=? and id=?", departmentId, patientId, consentId);
  }

  public List<Map<String, Object>> patientSummaries() {
    clinicianOrPatient();
    long patientId = portalPatient(); long departmentId = department();
    Boolean activeConsent = jdbc.queryForObject("select count(*)>0 from patient_consents where department_id=? and patient_id=? and consent_type='PORTAL_FOLLOW_UP_SUMMARY' and withdrawn_at is null", Boolean.class, departmentId, patientId);
    if (!Boolean.TRUE.equals(activeConsent)) return List.of();
    return jdbc.queryForList("""
        select r.id, r.patient_summary as summary, r.launched_at as approvedAt, r.reviewed_at as reviewedAt
        from care_workflow_runs r where r.department_id=? and r.patient_id=? and r.status in ('ACTIVE','COMPLETED')
          and r.patient_summary is not null and r.patient_summary<>'' order by r.launched_at desc
        """, departmentId, patientId);
  }

  @Transactional
  public Map<String, Object> cancel(long runId, CancelInput in) {
    clinician();
    long departmentId = department();
    int changed = jdbc.update("""
        update care_workflow_runs set status='CANCELLED', cancelled_by=?, cancelled_at=now(), version=version+1
         where department_id=? and id=? and status='ACTIVE' and version=?
        """, actor.user().getId(), departmentId, runId, in.version());
    if (changed != 1) {
      if (jdbc.queryForObject("select count(*) from care_workflow_runs where department_id=? and id=?", Integer.class, departmentId, runId) == 0) throw ApiException.missing();
      throw ApiException.conflict("RUN_CLOSED", "This workflow has already ended.");
    }
    jdbc.update("update care_tasks set status='CANCELLED', version=version+1, updated_at=now() where department_id=? and workflow_run_id=? and status in ('OPEN','IN_PROGRESS')", departmentId, runId);
    for (var task : taskRows("where t.department_id=? and t.workflow_run_id=? and t.status='CANCELLED'", departmentId, runId))
      publisher.publishEvent(new CareTaskChanged(departmentId, ((Number) task.get("id")).longValue(),
          (Long) task.get("assignedUserId"), (Instant) task.get("dueAt"), "CANCELLED"));
    audit.log("CARE_WORKFLOW_CANCELLED", "CareWorkflowRun", runId, "UI");
    return run(runId);
  }

  public Map<String, Object> run(long id) {
    long departmentId = department();
    clinician();
    var rows = jdbc.queryForList("""
        select r.id, r.template_id as templateId, r.workflow_version_id as workflowVersionId,
          v.version_number as workflowVersion, r.patient_id as patientId, r.admission_id as admissionId,
          r.trigger_type as trigger, r.status, r.version, r.source_reference as sourceReference,
          r.patient_summary as patientSummary, r.reviewed_by as reviewedBy, r.reviewed_at as reviewedAt,
          r.launched_by as launchedBy, r.launched_at as launchedAt, r.cancelled_at as cancelledAt
        from care_workflow_runs r join care_workflow_versions v on v.id=r.workflow_version_id
        where r.department_id=? and r.id=?
        """, departmentId, id);
    if (rows.isEmpty()) throw ApiException.missing();
    var result = new LinkedHashMap<String, Object>(rows.getFirst());
    result.put("tasks", taskRows("where t.department_id=? and t.workflow_run_id=? order by t.id", departmentId, id));
    return result;
  }

  public List<Map<String, Object>> runs(Long patientId) {
    clinician();
    long departmentId = department();
    if (patientId != null && !Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*)>0 from patients where department_id=? and id=?", Boolean.class, departmentId, patientId))) throw ApiException.missing();
    var scope = DepartmentContext.current();
    String filter = "";
    var params = new ArrayList<Object>(); params.add(departmentId);
    if ("DOCTOR".equals(scope.role())) { filter += " and exists(select 1 from admissions a where a.department_id=r.department_id and a.patient_id=r.patient_id and a.attending_doctor_id=?)"; params.add(scope.doctorId()); }
    if (patientId != null) { filter += " and r.patient_id=?"; params.add(patientId); }
    return jdbc.query("""
        select r.id, r.template_id as templateId, v.version_number as workflowVersion,
          r.patient_id as patientId, r.admission_id as admissionId, r.trigger_type as trigger,
          r.status, r.version, r.source_reference as sourceReference, r.reviewed_at as reviewedAt,
          r.launched_at as launchedAt, r.cancelled_at as cancelledAt
        from care_workflow_runs r join care_workflow_versions v on v.id=r.workflow_version_id
        where r.department_id=?
        """ + filter + " order by r.launched_at desc, r.id desc", (rs, n) -> {
          var row = new LinkedHashMap<String,Object>();
          row.put("id",rs.getLong("id")); row.put("templateId",rs.getLong("templateId"));
          row.put("workflowVersion",rs.getInt("workflowVersion")); row.put("patientId",rs.getLong("patientId"));
          long admission=rs.getLong("admissionId"); row.put("admissionId",rs.wasNull()?null:admission);
          row.put("trigger",rs.getString("trigger")); row.put("status",rs.getString("status"));
          row.put("sourceReference",rs.getString("sourceReference")); row.put("reviewedAt",rs.getTimestamp("reviewedAt").toInstant());
          row.put("launchedAt",rs.getTimestamp("launchedAt").toInstant());
          Timestamp cancelled=rs.getTimestamp("cancelledAt"); row.put("cancelledAt",cancelled==null?null:cancelled.toInstant());
          return row;
        }, params.toArray());
  }

  public List<Map<String, Object>> tasks() {
    clinician();
    long departmentId = department();
    var scope = DepartmentContext.current();
    if ("DOCTOR".equals(scope.role())) return taskRows("where t.department_id=? and t.assigned_user_id=? order by t.due_at nulls last, t.id", departmentId, actor.user().getId());
    return taskRows("where t.department_id=? order by t.due_at nulls last, t.id", departmentId);
  }

  public Map<String, Object> task(long id) {
    clinician();
    long departmentId = department();
    var rows = taskRows("where t.department_id=? and t.id=?", departmentId, id);
    if (rows.isEmpty()) throw ApiException.missing();
    visibleTask(rows.getFirst());
    return rows.getFirst();
  }

  @Transactional
  public Map<String, Object> updateTask(long id, TaskUpdate in) {
    clinician();
    long departmentId = department();
    var current = task(id);
    visibleTask(current);
    long version = ((Number) current.get("version")).longValue();
    if (version != in.version()) throw ApiException.conflict("STALE_STATE", "This task changed. Refresh before continuing.");
    String status = in.status().trim().toUpperCase(java.util.Locale.ROOT);
    if (!Set.of("OPEN", "IN_PROGRESS", "COMPLETED").contains(status)) throw invalid("Choose an available task status.");
    if ("CANCELLED".equals(current.get("status"))) throw ApiException.conflict("TASK_CANCELLED", "A cancelled task cannot be changed.");
    if ("COMPLETED".equals(current.get("status")) && !"COMPLETED".equals(status)) throw ApiException.conflict("TASK_COMPLETE", "A completed task cannot be reopened.");
    if ("BLOCKED".equals(current.get("dependencyState")) && !"OPEN".equals(status)) throw ApiException.conflict("TASK_BLOCKED", "Complete its dependencies before starting this task.");
    Long assignee = in.assignedUserId() == null ? (Long) current.get("assignedUserId") : in.assignedUserId();
    validateAssignee((String) current.get("ownerRole"), assignee);
    if ("DOCTOR".equals(DepartmentContext.current().role()) && !java.util.Objects.equals(assignee, actor.user().getId())) throw new AccessDeniedException("Doctors can update tasks assigned to them.");
    int changed = jdbc.update("""
        update care_tasks set status=?, assigned_user_id=?, version=version+1, updated_at=now()
         where department_id=? and id=? and version=?
        """, status, assignee, departmentId, id, in.version());
    if (changed != 1) throw ApiException.conflict("STALE_STATE", "This task changed. Refresh before continuing.");
    if ("COMPLETED".equals(status)) releaseReadyTasks(id, departmentId);
    audit.log("CARE_TASK_UPDATED", "CareTask", id, "UI", Map.of("status", status));
    var task = task(id);
    publisher.publishEvent(new CareTaskChanged(departmentId, id, assignee, (Instant) task.get("dueAt"), status));
    return task;
  }

  /** Called from admission create/discharge in the same transaction. Unique trigger source keys make retries idempotent. */
  @Transactional
  public void launchForTrigger(long admissionId, String trigger) {
    trigger = normalizeTrigger(trigger);
    if (!Set.of("ADMISSION", "DISCHARGE").contains(trigger)) return;
    long departmentId = department();
    Long patientId = jdbc.query("select patient_id from admissions where department_id=? and id=?",
        rs -> rs.next() ? rs.getLong(1) : null, departmentId, admissionId);
    if (patientId == null) return;
    String triggerFinal = trigger;
    var versions = jdbc.query("""
        select v.id, v.template_id, v.version_number, v.definition from care_workflow_versions v
        join care_workflow_templates t on t.id=v.template_id and t.department_id=v.department_id
        where v.department_id=? and t.published_version=v.version_number order by t.id
        """, (rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4)}, departmentId);
    for (Object[] row : versions) {
      Map<String, Object> definition = definition((String) row[3]);
      if (!listStrings(definition.get("triggers")).contains(triggerFinal)) continue;
      try {
        Target target = target(patientId, admissionId, true);
        launchDefinition((Long) row[1], (Long) row[0], (Integer) row[2], definition, target,
            triggerFinal, "admission:" + admissionId, null, "", actor.user().getId());
      } catch (org.springframework.dao.DuplicateKeyException alreadyLaunched) {
        // Idempotent delivery of the same admission lifecycle event.
      }
    }
  }

  private Map<String, Object> launchDefinition(long templateId, long versionId, int versionNumber,
      Map<String, Object> definition, Target target, String trigger, String sourceKey,
      String sourceReference, String summary, long reviewerId) {
    long departmentId = department();
    Instant now = Instant.now();
    Long runId = jdbc.query("""
          insert into care_workflow_runs(department_id,template_id,workflow_version_id,patient_id,admission_id,
            trigger_type,trigger_source_id,source_reference,patient_summary,reviewed_by,launched_by)
          values (?,?,?,?,?,?,?,?,?,?,?) on conflict(department_id,template_id,workflow_version_id,trigger_type,trigger_source_id)
          do nothing returning id
          """, rs -> rs.next() ? rs.getLong(1) : null, departmentId, templateId, versionId, target.patientId(), target.admissionId(), trigger,
          sourceKey, clean(sourceReference), clean(summary), reviewerId, actor.user().getId());
    if (runId == null) {
      var existing = jdbc.queryForObject("select id from care_workflow_runs where department_id=? and template_id=? and workflow_version_id=? and trigger_type=? and trigger_source_id=?", Long.class, departmentId, templateId, versionId, trigger, sourceKey);
      return run(existing);
    }
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) definition.get("tasks");
    Map<String, Long> taskIds = new HashMap<>();
    Map<String, List<String>> deps = new HashMap<>();
    for (Map<String, Object> item : tasks) {
      String key = (String) item.get("key");
      long dueOffset = ((Number) item.get("dueOffsetMinutes")).longValue();
      Timestamp dueAt = Timestamp.from(now.plusSeconds(Math.multiplyExact(dueOffset, 60)));
      String ownerRole = (String) item.get("ownerRole");
      Long assigned = item.get("assignedUserId") == null ? null : ((Number) item.get("assignedUserId")).longValue();
      if (assigned != null) validateAssignee(ownerRole, assigned);
      long taskId = jdbc.queryForObject("""
          insert into care_tasks(department_id,workflow_run_id,template_task_key,title,description,owner_role,
            assigned_user_id,due_at,dependency_state)
          values (?,?,?,?,?,?,?,?,?) returning id
          """, Long.class, departmentId, runId, key, item.get("title"), item.get("description"), ownerRole,
          assigned, dueAt, listStrings(item.get("dependsOn")).isEmpty() ? "READY" : "BLOCKED");
      taskIds.put(key, taskId);
      deps.put(key, listStrings(item.get("dependsOn")));
    }
    for (var entry : deps.entrySet()) for (String dependency : entry.getValue()) jdbc.update(
        "insert into care_task_dependencies(department_id,task_id,depends_on_task_id) values (?,?,?)",
        departmentId, taskIds.get(entry.getKey()), taskIds.get(dependency));
    audit.log("CARE_WORKFLOW_LAUNCHED", "CareWorkflowRun", runId, "UI",
        Map.of("templateId", templateId, "workflowVersion", versionNumber, "trigger", trigger, "taskCount", tasks.size()));
    for (var task : taskRows("where t.department_id=? and t.workflow_run_id=? order by t.id", departmentId, runId)) {
      publisher.publishEvent(new CareTaskChanged(departmentId, ((Number) task.get("id")).longValue(),
          (Long) task.get("assignedUserId"), (Instant) task.get("dueAt"), (String) task.get("status")));
    }
    return run(runId);
  }

  private Map<String, Object> previewView(long templateId, Long version, Target target, String trigger,
      Map<String, Object> definition, Instant base) {
    var items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> task : (List<Map<String, Object>>) definition.get("tasks")) {
      long offset = ((Number) task.get("dueOffsetMinutes")).longValue();
      var row = new LinkedHashMap<String, Object>(task);
      row.put("dueAt", offset == 0 ? null : base.plusSeconds(offset * 60));
      row.put("dependencyState", listStrings(task.get("dependsOn")).isEmpty() ? "READY" : "BLOCKED");
      items.add(row);
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("templateId", templateId); result.put("workflowVersion", version);
    result.put("patientId", target.patientId()); result.put("admissionId", target.admissionId());
    result.put("trigger", trigger); result.put("baseTime", base); result.put("tasks", items);
    return result;
  }

  private Target target(Long patientId, Long admissionId, boolean launch) {
    long departmentId = department();
    if (patientId == null && admissionId == null) throw invalid("Select a patient or admission.");
    Long resolvedPatient = patientId;
    if (admissionId != null) {
      var rows = jdbc.query("select patient_id, attending_doctor_id, status from admissions where department_id=? and id=?",
          (rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getString(3)}, departmentId, admissionId);
      if (rows.isEmpty()) throw ApiException.missing();
      Object[] admission = rows.getFirst();
      if (resolvedPatient != null && !resolvedPatient.equals(admission[0])) throw invalid("Admission and patient do not match.");
      resolvedPatient = (Long) admission[0];
      if (launch && "CANCELLED".equals(admission[2])) throw invalid("Cancelled admissions cannot receive a workflow.");
      if ("DOCTOR".equals(DepartmentContext.current().role()) && !java.util.Objects.equals(DepartmentContext.current().doctorId(), admission[1])) throw new AccessDeniedException("This patient is outside your assigned care.");
    }
    if (resolvedPatient == null || !Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from patients where department_id=? and id=?", Boolean.class, departmentId, resolvedPatient))) throw ApiException.missing();
    if (admissionId == null && "DOCTOR".equals(DepartmentContext.current().role())) {
      Boolean assigned = jdbc.queryForObject("select count(*)>0 from admissions where department_id=? and patient_id=? and attending_doctor_id=?", Boolean.class, departmentId, resolvedPatient, DepartmentContext.current().doctorId());
      if (!Boolean.TRUE.equals(assigned)) throw new AccessDeniedException("This patient is outside your assigned care.");
    }
    return new Target(resolvedPatient, admissionId);
  }

  private void validateAssignee(String role, Long userId) {
    if (userId == null) return;
    long departmentId = department();
    Boolean valid = jdbc.queryForObject("""
        select count(*)>0 from department_memberships m
        join app_users u on u.id=m.user_id and u.enabled=true
        where m.department_id=? and m.user_id=? and m.role=?
        """, Boolean.class, departmentId, userId, role);
    if (!Boolean.TRUE.equals(valid)) throw invalid("The task owner must be an active department member with the selected role.");
  }

  private void requirePortalSummaryConsent(long patientId) {
    Boolean consented = jdbc.queryForObject("select count(*)>0 from patient_consents where department_id=? and patient_id=? and consent_type='PORTAL_FOLLOW_UP_SUMMARY' and withdrawn_at is null", Boolean.class, department(), patientId);
    if (!Boolean.TRUE.equals(consented)) throw new ApiException(403, "PORTAL_CONSENT_REQUIRED", "Active patient consent is required before publishing a follow-up summary.");
  }

  private long portalPatient() {
    var user = actor.user();
    if ("PATIENT".equals(DepartmentContext.current().role())) {
      if (user.getPatientId() == null) throw ApiException.missing();
      return user.getPatientId();
    }
    throw new AccessDeniedException("Patient portal access is required.");
  }
  private void clinicianOrPatient() {
    String role = DepartmentContext.current() == null ? null : DepartmentContext.current().role();
    if (!Set.of("ADMIN", "MEDICAL_STAFF", "DOCTOR", "PATIENT").contains(role)) throw new AccessDeniedException("Department access is required.");
  }

  private void releaseReadyTasks(long completedTaskId, long departmentId) {
    var readyIds = jdbc.queryForList("""
        select t.id from care_tasks t
         where t.department_id=? and t.dependency_state='BLOCKED'
           and exists(select 1 from care_task_dependencies d where d.department_id=? and d.task_id=t.id and d.depends_on_task_id=?)
           and not exists(select 1 from care_task_dependencies d join care_tasks p on p.id=d.depends_on_task_id
             where d.department_id=? and d.task_id=t.id and p.status<>'COMPLETED')
        """, Long.class, departmentId, departmentId, completedTaskId, departmentId);
    jdbc.update("""
        update care_tasks t set dependency_state='READY', version=version+1, updated_at=now()
         where t.department_id=? and t.dependency_state='BLOCKED'
           and exists(select 1 from care_task_dependencies d where d.department_id=? and d.task_id=t.id and d.depends_on_task_id=?)
           and not exists(select 1 from care_task_dependencies d join care_tasks p on p.id=d.depends_on_task_id
             where d.department_id=? and d.task_id=t.id and p.status<>'COMPLETED')
        """, departmentId, departmentId, completedTaskId, departmentId);
    for (Long readyId : readyIds) {
      var ready = taskRows("where t.department_id=? and t.id=?", departmentId, readyId).getFirst();
      publisher.publishEvent(new CareTaskChanged(departmentId, readyId,
          (Long) ready.get("assignedUserId"), (Instant) ready.get("dueAt"), (String) ready.get("status")));
    }
  }

  private List<Map<String, Object>> taskRows(String where, Object... args) {
    var rows = jdbc.query("""
        select t.id, t.workflow_run_id as workflowRunId, t.template_task_key as taskKey, t.title,
          r.patient_id as patientId,
          t.description, t.owner_role as ownerRole, t.assigned_user_id as assignedUserId, t.due_at as dueAt,
          t.status, t.dependency_state as dependencyState, t.version, t.created_at as createdAt, t.updated_at as updatedAt,
          t.department_id as departmentId
        from care_tasks t join care_workflow_runs r on r.department_id=t.department_id and r.id=t.workflow_run_id """ + where, (rs, n) -> mapTask(rs), args);
    for (var row : rows) {
      long id = ((Number) row.get("id")).longValue();
      row.put("dependsOn", jdbc.queryForList("""
          select p.template_task_key from care_task_dependencies d join care_tasks p on p.id=d.depends_on_task_id
           where d.department_id=? and d.task_id=? order by p.id
          """, String.class, row.get("departmentId"), id));
      row.remove("departmentId");
    }
    return rows;
  }

  private Map<String, Object> mapTask(ResultSet rs) throws SQLException {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", rs.getLong("id")); row.put("workflowRunId", rs.getLong("workflowRunId"));
    row.put("patientId", rs.getLong("patientId"));
    row.put("key", rs.getString("taskKey")); row.put("title", rs.getString("title"));
    row.put("description", rs.getString("description")); row.put("ownerRole", rs.getString("ownerRole"));
    long assignee = rs.getLong("assignedUserId"); row.put("assignedUserId", rs.wasNull() ? null : assignee);
    Timestamp due = rs.getTimestamp("dueAt"); row.put("dueAt", due == null ? null : due.toInstant());
    row.put("status", rs.getString("status")); row.put("dependencyState", rs.getString("dependencyState"));
    row.put("version", rs.getLong("version")); row.put("createdAt", rs.getTimestamp("createdAt").toInstant());
    row.put("updatedAt", rs.getTimestamp("updatedAt").toInstant());
    return row;
  }

  private void visibleTask(Map<String, Object> task) {
    if ("DOCTOR".equals(DepartmentContext.current().role()) && !java.util.Objects.equals(task.get("assignedUserId"), actor.user().getId())) throw new AccessDeniedException("This task is outside your assignment.");
  }

  private Map<String, Object> template(long id) {
    var rows = jdbc.queryForList("select id,name,description,draft_definition,version,published_version from care_workflow_templates where department_id=? and id=?", department(), id);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }
  private Map<String, Object> version(long templateId, long versionNumber) {
    var rows = jdbc.queryForList("select id,version_number,definition from care_workflow_versions where department_id=? and template_id=? and version_number=?", department(), templateId, versionNumber);
    if (rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
  }
  private Map<String, Object> versionById(long id, long templateId) {
    var rows = jdbc.queryForList("select id,version_number,definition from care_workflow_versions where department_id=? and template_id=? and id=?", department(), templateId, id);
    if (rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
  }
  private boolean existsTemplate(long id, long departmentId) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from care_workflow_templates where department_id=? and id=?", Boolean.class, departmentId, id));
  }

  private Map<String, Object> normalize(CareWorkflowInput in) {
    if (in.tasks() == null || in.tasks().isEmpty() || in.tasks().size() > 100) throw invalid("Add between 1 and 100 tasks.");
    var triggers = new ArrayList<String>();
    for (String trigger : in.triggers()) {
      String value = normalizeTrigger(trigger);
      if (!triggers.add(value)) throw invalid("Each trigger may appear only once.");
    }
    if (triggers.isEmpty()) throw invalid("Choose at least one trigger.");
    var byKey = new LinkedHashMap<String, Task>();
    for (Task task : in.tasks()) {
      String key = task.key().trim();
      if (!key.matches("[A-Za-z0-9_-]{1,80}") || byKey.putIfAbsent(key, task) != null) throw invalid("Task keys must be unique letters, numbers, underscores, or hyphens.");
      String role = task.ownerRole().trim().toUpperCase(java.util.Locale.ROOT);
      if (!OWNER_ROLES.contains(role)) throw invalid("Choose a valid task owner role.");
      if (task.assignedUserId() != null) validateAssignee(role, task.assignedUserId());
      if (task.dueOffsetMinutes() < 0 || task.dueOffsetMinutes() > 525600) throw invalid("Due offsets must be between 0 and 525600 minutes.");
      if (task.dependsOn() == null) throw invalid("Dependencies must be a list.");
    }
    var tasks = new ArrayList<Map<String, Object>>();
    for (Task task : in.tasks()) {
      var dependencies = new ArrayList<String>();
      for (String dep : task.dependsOn()) {
        if (!byKey.containsKey(dep) || dep.equals(task.key()) || !dependencies.add(dep)) throw invalid("Task dependencies must reference a different task exactly once.");
      }
      var role = task.ownerRole().trim().toUpperCase(java.util.Locale.ROOT);
      var row = new LinkedHashMap<String, Object>();
      row.put("key", task.key().trim()); row.put("title", task.title().trim()); row.put("description", clean(task.description()));
      row.put("ownerRole", role); row.put("assignedUserId", task.assignedUserId());
      row.put("dueOffsetMinutes", task.dueOffsetMinutes()); row.put("dependsOn", dependencies);
      tasks.add(row);
    }
    rejectCycles(tasks);
    var result = new LinkedHashMap<String, Object>();
    result.put("triggers", triggers); result.put("tasks", tasks);
    return result;
  }

  private void validateDecoded(Map<String, Object> definition) {
    List<Map<String, Object>> tasks = (List<Map<String, Object>>) definition.get("tasks");
    if (tasks == null || tasks.isEmpty() || tasks.size() > 100) throw invalid("The draft needs 1 to 100 tasks.");
    var keys = new HashSet<String>();
    for (var task : tasks) if (!(task.get("key") instanceof String key) || !keys.add(key)) throw invalid("Task keys must be unique.");
    for (var task : tasks) {
      String role = (String) task.get("ownerRole");
      if (!OWNER_ROLES.contains(role)) throw invalid("Choose a valid owner role.");
      if (task.get("assignedUserId") instanceof Number assignee) validateAssignee(role, assignee.longValue());
      for (String dep : listStrings(task.get("dependsOn"))) if (!keys.contains(dep)) throw invalid("Every dependency must reference a task in this template.");
    }
    rejectCycles(tasks);
  }

  private static void rejectCycles(List<Map<String, Object>> tasks) {
    var graph = new HashMap<String, List<String>>();
    for (var task : tasks) graph.put((String) task.get("key"), listStrings(task.get("dependsOn")));
    var visiting = new HashSet<String>(); var visited = new HashSet<String>();
    for (String key : graph.keySet()) if (cycle(key, graph, visiting, visited)) throw invalid("Task dependencies cannot contain a cycle.");
  }
  private static boolean cycle(String node, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited) {
    if (visited.contains(node)) return false;
    if (!visiting.add(node)) return true;
    for (String dep : graph.getOrDefault(node, List.of())) if (cycle(dep, graph, visiting, visited)) return true;
    visiting.remove(node); visited.add(node); return false;
  }

  private static List<String> listStrings(Object value) {
    if (!(value instanceof List<?> values)) return List.of();
    return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }
  private static String normalizeTrigger(String value) {
    String trigger = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    if (!TRIGGERS.contains(trigger)) throw invalid("Choose MANUAL, ADMISSION, or DISCHARGE.");
    return trigger;
  }
  private static void requireTrigger(Map<String, Object> definition, String trigger) {
    if (!listStrings(definition.get("triggers")).contains(trigger)) throw invalid("This template does not support the selected trigger.");
  }
  private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private Map<String, Object> definition(String source) {
    try { return mapper.readValue(source, mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)); }
    catch (Exception e) { throw new ApiException(500, "WORKFLOW_DATA_INVALID", "The saved workflow definition could not be read."); }
  }
  private String json(Object value) {
    try { return mapper.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize care workflow definition", e); }
  }
  private void clinician() {
    String role = DepartmentContext.current() == null ? null : DepartmentContext.current().role();
    if (!Set.of("ADMIN", "MEDICAL_STAFF", "DOCTOR").contains(role)) throw new AccessDeniedException("Department clinical access is required.");
  }
  private static long department() {
    long id = DepartmentContext.id();
    if (id <= 0) throw new ApiException(403, "DEPARTMENT_REQUIRED", "Open a department to manage care workflows.");
    return id;
  }
  private static ApiException invalid(String message) { return new ApiException(400, "INVALID_CARE_WORKFLOW", message); }
  private record Target(long patientId, Long admissionId) {}
}
