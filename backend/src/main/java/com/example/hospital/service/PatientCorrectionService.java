package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.Patient;
import com.example.hospital.repository.PatientRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient-submitted demographic corrections (#159).
 *
 * <p>A patient may ask to correct their own contact details from the portal, but
 * never edits the record directly. Staff review each request and approval applies
 * the value under the same optimistic version check as any other patient edit, so
 * the audit trail shows the proposal and the decision separately.
 */
@Service
public class PatientCorrectionService {
  private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");
  private static final Set<String> FIELDS =
      Set.of("ADDRESS", "PHONE_NUMBER", "FIRST_NAME", "LAST_NAME");
  private static final Set<String> CONTACT_FIELDS = Set.of("ADDRESS", "PHONE_NUMBER");

  private final Actor actor;
  private final JdbcTemplate jdbc;
  private final PatientRepository patients;
  private final AuditService audit;

  public PatientCorrectionService(
      Actor actor, JdbcTemplate jdbc, PatientRepository patients, AuditService audit) {
    this.actor = actor;
    this.jdbc = jdbc;
    this.patients = patients;
    this.audit = audit;
  }

  public record RequestInput(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40)
          String fieldName,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
          String requestedValue,
      @jakarta.validation.constraints.Size(max = 500) String reason) {}

  public record ReviewInput(
      @jakarta.validation.constraints.NotNull Long version, @jakarta.validation.constraints.Size(max = 500) String note) {}

  /** The signed-in patient's own record, or missing when the account has no patient linked. */
  private long portalPatient() {
    var user = actor.user();
    var scope = DepartmentContext.current();
    if (scope == null || !"PATIENT".equals(scope.role()))
      throw new AccessDeniedException("Patient portal access is required.");
    if (user.getPatientId() == null) throw ApiException.missing();
    return user.getPatientId();
  }

  private long department() {
    return DepartmentContext.id();
  }

  public List<Map<String, Object>> myRequests() {
    long patientId = portalPatient();
    return jdbc.queryForList(
        """
        select id, field_name as "fieldName", previous_value as "previousValue",
          requested_value as "requestedValue", reason, status, patient_version as "version",
          review_note as "reviewNote", created_at as "createdAt", reviewed_at as "reviewedAt"
        from patient_correction_requests
        where department_id=? and patient_id=?
        order by created_at desc, id desc
        """,
        department(),
        patientId);
  }

  @Transactional
  public Map<String, Object> submit(RequestInput in) {
    long patientId = portalPatient();
    long departmentId = department();
    String field = normalizeField(in.fieldName());
    if (!FIELDS.contains(field))
      throw new ApiException(400, "UNSUPPORTED_FIELD", "Choose a field that can be corrected.");
    String value = in.requestedValue().trim();
    if (value.isEmpty())
      throw new ApiException(400, "VALUE_REQUIRED", "Enter the corrected value.");
    if ("PHONE_NUMBER".equals(field) && !E164.matcher(value).matches())
      throw new ApiException(
          400, "INVALID_PHONE", "Use E.164 format, for example +359****3456.");

    var patient = patients.findById(patientId).orElseThrow(ApiException::missing);
    String previous = currentValue(patient, field);
    if (value.equals(previous == null ? "" : previous))
      throw ApiException.conflict(
          "ALREADY_CORRECT", "That is already the value on your record.");
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*)>0 from patient_correction_requests"
            + " where department_id=? and patient_id=? and field_name=? and status='PENDING'",
        Boolean.class, departmentId, patientId, field)))
      throw ApiException.conflict(
          "REQUEST_PENDING", "You already have an open request for this field.");

    Long id = jdbc.queryForObject(
        """
        insert into patient_correction_requests
          (department_id, patient_id, requested_by, field_name, previous_value,
           requested_value, reason, patient_version)
        values (?,?,?,?,?,?,?,?) returning id
        """,
        Long.class,
        departmentId,
        patientId,
        actor.user().getId(),
        field,
        previous,
        value,
        blankToNull(in.reason()),
        patient.getVersion());
    audit.log(
        "PATIENT_CORRECTION_REQUESTED",
        "PatientCorrectionRequest",
        id,
        "PORTAL",
        Map.of("patientId", patientId, "fieldName", field));
    return detail(departmentId, id);
  }

  /** Staff queue for this department, optionally narrowed to one status. */
  public Map<String, Object> queue(String status, int page, int size) {
    actor.staff();
    long departmentId = department();
    String filter = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    if (!filter.isEmpty() && !Set.of("PENDING", "APPROVED", "REJECTED").contains(filter))
      throw new ApiException(400, "INVALID_STATUS", "Choose a known request status.");
    int from = Math.max(0, page) * Math.max(1, size);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select r.id, r.field_name as "fieldName", r.previous_value as "previousValue",
              r.requested_value as "requestedValue", r.reason, r.status,
              r.patient_version as "version",
              r.review_note as "reviewNote", r.created_at as "createdAt",
              r.reviewed_at as "reviewedAt", r.patient_id as "patientId",
              p.first_name as "patientFirstName", p.last_name as "patientLastName",
              p.patient_identifier as "patientIdentifier"
            from patient_correction_requests r
            join patients p on p.id = r.patient_id and p.department_id = r.department_id
            where r.department_id=? and (? = '' or r.status = ?)
            order by case when r.status = 'PENDING' then 0 else 1 end, r.created_at desc, r.id desc
            limit ? offset ?
            """,
            departmentId,
            filter,
            filter,
            Math.max(1, size),
            from);
    Integer total = jdbc.queryForObject(
        "select count(*) from patient_correction_requests where department_id=? and (? = '' or status = ?)",
        Integer.class, departmentId, filter, filter);
    return Map.of(
        "requests", rows, "page", Math.max(0, page), "size", Math.max(1, size), "total", total);
  }

  @Transactional
  public Map<String, Object> review(long id, boolean approve, ReviewInput in) {
    actor.staff();
    long departmentId = department();
    var rows = jdbc.queryForList(
        "select patient_id, field_name, previous_value, requested_value, status, patient_version"
            + " from patient_correction_requests where department_id=? and id=? for update",
        departmentId, id);
    if (rows.isEmpty()) throw ApiException.missing();
    var row = rows.getFirst();
    if (!"PENDING".equals(row.get("status")))
      throw ApiException.conflict("ALREADY_REVIEWED", "This request was already reviewed.");
    Long requestVersion = ((Number) row.get("patient_version")).longValue();
    if (!requestVersion.equals(in.version()))
      throw ApiException.conflict("STALE_STATE", "This request changed. Reload and try again.");
    long patientId = ((Number) row.get("patient_id")).longValue();
    String field = (String) row.get("field_name");
    String value = (String) row.get("requested_value");
    String previous = (String) row.get("previous_value");

    if (approve) applyToPatient(patientId, field, previous, value);

    jdbc.update(
        "update patient_correction_requests set status=?, reviewed_by=?, reviewed_at=now(),"
            + " review_note=?, updated_at=now() where department_id=? and id=?",
        approve ? "APPROVED" : "REJECTED",
        actor.user().getId(),
        blankToNull(in.note()),
        departmentId,
        id);
    audit.log(
        approve ? "PATIENT_CORRECTION_APPROVED" : "PATIENT_CORRECTION_REJECTED",
        "PatientCorrectionRequest",
        id,
        "UI",
        Map.of("patientId", patientId, "fieldName", field, "requestedValue", value));
    return detail(departmentId, id);
  }

  /**
   * Applies an approved value to the patient row, refusing if the field no longer
   * holds the value the request was raised against, so an approval cannot silently
   * overwrite a later clinical edit.
   */
  private void applyToPatient(long patientId, String field, String previous, String value) {
    var patient = patients.findById(patientId).orElseThrow(ApiException::missing);
    if (patient.getDepartmentId() != department())
      throw new AccessDeniedException("Patient belongs to another department.");
    if (!java.util.Objects.equals(currentValue(patient, field), previous))
      throw ApiException.conflict(
          "PATIENT_CHANGED", "The patient record changed. Recheck before approving.");
    switch (field) {
      case "ADDRESS" -> patient.setAddress(value);
      case "PHONE_NUMBER" -> patient.setPhoneNumber(value);
      case "FIRST_NAME" -> patient.setFirstName(value);
      default -> patient.setLastName(value);
    }
    patients.saveAndFlush(patient);
    audit.log(
        "PATIENT_UPDATED",
        "Patient",
        patientId,
        "UI",
        Map.of("fieldName", field, "source", "PATIENT_CORRECTION"));
  }

  /**
   * Accepts the field names the portal sends ({@code phoneNumber}) as well as the
   * stored form ({@code PHONE_NUMBER}), so the client is not forced to know the
   * database's spelling.
   */
  private static String normalizeField(String raw) {
    String trimmed = raw.trim();
    return switch (trimmed) {
      case "address", "ADDRESS" -> "ADDRESS";
      case "phoneNumber", "phone_number", "PHONE_NUMBER" -> "PHONE_NUMBER";
      case "firstName", "first_name", "FIRST_NAME" -> "FIRST_NAME";
      case "lastName", "last_name", "LAST_NAME" -> "LAST_NAME";
      default -> trimmed.toUpperCase(Locale.ROOT);
    };
  }

  private static String currentValue(Patient patient, String field) {
    return switch (field) {
      case "ADDRESS" -> patient.getAddress();
      case "PHONE_NUMBER" -> patient.getPhoneNumber();
      case "FIRST_NAME" -> patient.getFirstName();
      case "LAST_NAME" -> patient.getLastName();
      default -> throw new IllegalArgumentException(field);
    };
  }

  private static String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private Map<String, Object> detail(long departmentId, long id) {
    return jdbc.queryForMap(
        """
        select id, field_name as "fieldName", previous_value as "previousValue",
          requested_value as "requestedValue", reason, status, patient_version as "version",
          review_note as "reviewNote", created_at as "createdAt", reviewed_at as "reviewedAt"
        from patient_correction_requests where department_id=? and id=?
        """,
        departmentId,
        id);
  }

  /** Fields a patient may correct; exposed so the portal does not hard-code the list. */
  public static List<Map<String, String>> supportedFields() {
    return List.of(
        Map.of("fieldName", "ADDRESS", "label", "Home address"),
        Map.of("fieldName", "PHONE_NUMBER", "label", "Phone number"),
        Map.of("fieldName", "FIRST_NAME", "label", "First name"),
        Map.of("fieldName", "LAST_NAME", "label", "Last name"));
  }

  static boolean isContactField(String field) {
    return CONTACT_FIELDS.contains(field);
  }
}
