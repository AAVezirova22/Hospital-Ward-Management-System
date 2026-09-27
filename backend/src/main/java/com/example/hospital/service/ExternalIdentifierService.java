package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-system identifier mapping (#402). An external identifier ({@code namespace} + {@code value})
 * maps to exactly one local record per department, and a record has at most one identifier per
 * namespace. Conflicting links are refused, never overwritten, so an integration cannot silently
 * move an identifier to a different patient.
 */
@Service
public class ExternalIdentifierService {
  private static final Map<String, String> TABLES = Map.of(
      "PATIENT", "patients", "ADMISSION", "admissions", "DOCTOR", "doctors", "ROOM", "rooms",
      "PROCEDURE", "medical_procedures");
  private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._/-]{0,99}");
  private static final String COLUMNS =
      "id, entity_type, entity_id, namespace, value, source, created_at";

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;

  public ExternalIdentifierService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
  }

  public List<Map<String, Object>> forRecord(String entityType, long entityId) {
    String type = type(entityType);
    return jdbc.queryForList(
        "select " + COLUMNS + " from external_identifiers where department_id = ? and entity_type = ? and entity_id = ?"
            + " order by namespace",
        DepartmentContext.id(), type, entityId);
  }

  public Map<String, Object> resolve(String entityType, String namespace, String value) {
    var rows = jdbc.queryForList(
        "select " + COLUMNS + " from external_identifiers where department_id = ? and entity_type = ?"
            + " and namespace = ? and value = ?",
        DepartmentContext.id(), type(entityType), namespace(namespace), value(value));
    if (rows.isEmpty()) throw new ApiException(404, "EXTERNAL_ID_NOT_FOUND", "No local record uses this external identifier.");
    return rows.getFirst();
  }

  /** Links an identifier; repeating an identical link returns the existing row. */
  @Transactional
  public Map<String, Object> link(String entityType, long entityId, String namespace, String value, String source) {
    actor.staff();
    String type = type(entityType);
    String ns = namespace(namespace);
    String val = value(value);
    String origin = source == null || source.isBlank() ? null : source.strip();
    if (origin != null && origin.length() > 100)
      throw new ApiException(400, "VALIDATION_ERROR", "Source must be at most 100 characters.");
    long department = DepartmentContext.id();
    Boolean exists = jdbc.queryForObject(
        "select exists (select 1 from " + TABLES.get(type) + " where id = ? and department_id = ?)",
        Boolean.class, entityId, department);
    if (!Boolean.TRUE.equals(exists)) throw ApiException.missing();
    // Serialise links for the same identifier or record so concurrent imports cannot both win.
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtext(?))", Object.class, department + ":" + type + ":" + ns);
    var byValue = jdbc.queryForList(
        "select " + COLUMNS + " from external_identifiers where department_id = ? and entity_type = ? and namespace = ? and value = ?",
        department, type, ns, val);
    if (!byValue.isEmpty()) {
      var existing = byValue.getFirst();
      if (((Number) existing.get("entity_id")).longValue() == entityId) return existing;
      throw new ApiException(409, "EXTERNAL_ID_CONFLICT",
          "This external identifier already belongs to another record. Unlink it there first.");
    }
    var byRecord = jdbc.queryForList(
        "select id from external_identifiers where department_id = ? and entity_type = ? and entity_id = ? and namespace = ?",
        department, type, entityId, ns);
    if (!byRecord.isEmpty())
      throw new ApiException(409, "EXTERNAL_ID_NAMESPACE_TAKEN",
          "This record already has an identifier in that namespace. Unlink it before adding another.");
    long id = jdbc.queryForObject(
        "insert into external_identifiers(department_id, entity_type, entity_id, namespace, value, source, created_by)"
            + " values (?,?,?,?,?,?,?) returning id",
        Long.class, department, type, entityId, ns, val, origin, actor.user().getId());
    audit.log("EXTERNAL_ID_LINKED", entityLabel(type), entityId, "UI", Map.of("namespace", ns, "source", origin == null ? "" : origin));
    return jdbc.queryForMap("select " + COLUMNS + " from external_identifiers where id = ?", id);
  }

  @Transactional
  public void unlink(long id) {
    actor.admin();
    var rows = jdbc.queryForList(
        "select entity_type, entity_id, namespace from external_identifiers where id = ? and department_id = ?",
        id, DepartmentContext.id());
    if (rows.isEmpty()) throw ApiException.missing();
    jdbc.update("delete from external_identifiers where id = ?", id);
    var row = rows.getFirst();
    audit.log("EXTERNAL_ID_UNLINKED", entityLabel(String.valueOf(row.get("entity_type"))),
        ((Number) row.get("entity_id")).longValue(), "UI", Map.of("namespace", row.get("namespace")));
  }

  private static String type(String entityType) {
    String type = entityType == null ? "" : entityType.strip().toUpperCase(Locale.ROOT);
    if (!TABLES.containsKey(type))
      throw new ApiException(400, "INVALID_ENTITY_TYPE", "Entity type must be PATIENT, ADMISSION, DOCTOR, ROOM or PROCEDURE.");
    return type;
  }

  private static String namespace(String namespace) {
    String ns = namespace == null ? "" : namespace.strip();
    if (!NAMESPACE.matcher(ns).matches())
      throw new ApiException(400, "INVALID_NAMESPACE", "Namespace must be 1-100 letters, digits or : . _ / - characters.");
    return ns;
  }

  private static String value(String value) {
    String val = value == null ? "" : value.strip();
    if (val.isEmpty() || val.length() > 200 || val.chars().anyMatch(Character::isISOControl))
      throw new ApiException(400, "INVALID_EXTERNAL_ID", "External identifier must be 1-200 printable characters.");
    return val;
  }

  private static String entityLabel(String type) {
    return type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT);
  }
}
