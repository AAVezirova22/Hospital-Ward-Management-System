package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.DoctorInput;
import com.example.hospital.api.PatientInput;
import com.example.hospital.api.ProcedureInput;
import com.example.hospital.api.RoomInput;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previewed CSV import for initial setup (#161).
 *
 * <p>Upload parses and validates into a staging batch and writes nothing
 * operational. The administrator reviews the per-row verdict and only then
 * commits. This matters for clinical data: a preview that half-applies is worse
 * than no import, so the two phases are separate transactions by construction.
 */
@Service
public class CsvImportService {
  public static final int MAX_ROWS = 2000;
  public static final int MAX_BYTES = 2 * 1024 * 1024;
  private static final Set<String> TYPES = Set.of("PATIENT", "DOCTOR", "ROOM", "PROCEDURE");

  private final Actor actor;
  private final JdbcTemplate jdbc;
  private final CatalogueService catalogue;
  private final PatientService patients;
  private final AuditService audit;

  public CsvImportService(
      Actor actor, JdbcTemplate jdbc, CatalogueService catalogue, PatientService patients,
      AuditService audit) {
    this.actor = actor;
    this.jdbc = jdbc;
    this.catalogue = catalogue;
    this.patients = patients;
    this.audit = audit;
  }

  /** Headers per entity type; an unknown or missing one is refused before anything is stored. */
  private static final Map<String, List<String>> HEADERS = Map.of(
      "PATIENT", List.of("patientIdentifier", "firstName", "lastName", "dateOfBirth", "address", "phoneNumber"),
      "DOCTOR", List.of("doctorIdentifier", "firstName", "lastName", "specialty"),
      "ROOM", List.of("roomNumber", "bedCount"),
      "PROCEDURE", List.of("procedureCode", "procedureName", "currentCost"));

  private long department() {
    return DepartmentContext.id();
  }

  private void admin() {
    actor.admin();
  }

  public List<Map<String, String>> templates() {
    return HEADERS.entrySet().stream()
        .map(e -> Map.of("entityType", e.getKey(),
            "columns", String.join(",", e.getValue())))
        .toList();
  }

  @Transactional
  public Map<String, Object> preview(
      String entityType, String fileName, String content, String duplicatePolicy) {
    admin();
    String type = entityType.trim().toUpperCase(Locale.ROOT);
    if (!TYPES.contains(type))
      throw new ApiException(400, "UNSUPPORTED_ENTITY", "Choose patients, doctors, rooms or procedures.");
    String policy = duplicatePolicy == null || duplicatePolicy.isBlank()
        ? "SKIP" : duplicatePolicy.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("SKIP", "REJECT").contains(policy))
      throw new ApiException(400, "UNSUPPORTED_POLICY", "Duplicates are either skipped or reject the batch.");
    if (content == null || content.isBlank())
      throw new ApiException(400, "EMPTY_FILE", "The file has no rows.");
    if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new ApiException(400, "FILE_TOO_LARGE", "Import at most 2 MB per file.");

    String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    int headerIndex = 0;
    while (headerIndex < lines.length && lines[headerIndex].isBlank()) headerIndex++;
    if (headerIndex >= lines.length)
      throw new ApiException(400, "EMPTY_FILE", "The file has no header row.");
    List<String> header = splitCsvLine(lines[headerIndex]);
    if (!header.equals(HEADERS.get(type)))
      throw new ApiException(400, "UNEXPECTED_HEADER",
          "Expected columns: " + String.join(",", HEADERS.get(type)));

    Long batchId = jdbc.queryForObject("""
        insert into csv_import_batches(department_id,entity_type,file_name,duplicate_policy,total_rows,created_by)
        values (?,?,?,?,?,?) returning id
        """, Long.class, department(), type,
        fileName == null || fileName.isBlank() ? "import.csv" : fileName.trim(), policy, 0,
        actor.user().getId());

    int rowNumber = 0;
    int ready = 0, duplicates = 0, invalid = 0;
    for (int i = headerIndex + 1; i < lines.length; i++) {
      if (lines[i].isBlank()) continue;
      rowNumber++;
      if (rowNumber > MAX_ROWS)
        throw new ApiException(400, "TOO_MANY_ROWS", "Import at most " + MAX_ROWS + " rows per file.");
      List<String> cells = splitCsvLine(lines[i]);
      var outcome = validateRow(type, cells, policy);
      if (outcome.duplicate()) duplicates++;
      else if (!outcome.problems().isEmpty()) invalid++;
      else ready++;
      jdbc.update("""
          insert into csv_import_rows(batch_id,row_number,raw_row,parsed_value,status,problems)
          values (?,?,?,?,?,?)
          """, batchId, rowNumber, String.join(",", cells),
          outcome.input() == null ? null : json(outcome.input()),
          outcome.duplicate() ? "SKIPPED_DUPLICATE"
              : outcome.problems().isEmpty() ? "READY" : "INVALID",
          outcome.problems().isEmpty() ? null : String.join(" ", outcome.problems()));
    }
    if (rowNumber == 0)
      throw new ApiException(400, "EMPTY_FILE", "The file has a header but no data rows.");

    jdbc.update("update csv_import_batches set total_rows=? where id=?", rowNumber, batchId);
    audit.log("CSV_IMPORT_PREVIEWED", "CsvImportBatch", batchId, "UI",
        Map.of("entityType", type, "rows", rowNumber, "ready", ready));
    return summary(batchId);
  }

  @Transactional
  public Map<String, Object> commit(long batchId) {
    admin();
    long departmentId = department();
    var batches = jdbc.queryForList(
        "select entity_type, status, duplicate_policy from csv_import_batches"
            + " where department_id=? and id=? for update",
        departmentId, batchId);
    if (batches.isEmpty()) throw ApiException.missing();
    var batch = batches.getFirst();
    if ("COMMITTED".equals(batch.get("status")))
      throw ApiException.conflict("ALREADY_COMMITTED", "This import was already committed.");
    if (!"PREVIEWED".equals(batch.get("status")))
      throw ApiException.conflict("NOT_COMMITTABLE", "This import is no longer awaiting review.");
    String type = (String) batch.get("entity_type");

    int created = 0, skipped = 0;
    var rows = jdbc.queryForList(
        "select id, row_number, status, problems, parsed_value from csv_import_rows"
            + " where batch_id=? order by row_number",
        batchId);
    for (var row : rows) {
      String status = (String) row.get("status");
      long rowId = ((Number) row.get("id")).longValue();
      if ("SKIPPED_DUPLICATE".equals(status)) { skipped++; continue; }
      if (!"READY".equals(status)) { skipped++; continue; }
      String problem = (String) row.get("problems");
      if (problem != null) { skipped++; continue; }
      Object parsed = row.get("parsed_value");
      Long recordId = createRecord(type, parsed);
      if (recordId == null) { skipped++; continue; }
      jdbc.update("update csv_import_rows set created_record_id=? where id=?", recordId, rowId);
      created++;
    }

    jdbc.update("update csv_import_batches set status='COMMITTED', committed_by=?, committed_at=now(),"
        + " updated_at=now() where id=?", actor.user().getId(), batchId);
    audit.log("CSV_IMPORT_COMMITTED", "CsvImportBatch", batchId, "UI",
        Map.of("entityType", type, "created", created, "skipped", skipped));
    var result = summary(batchId);
    result.put("created", created);
    result.put("skipped", skipped);
    return result;
  }

  @Transactional
  public Map<String, Object> discard(long batchId) {
    admin();
    int changed = jdbc.update(
        "update csv_import_batches set status='DISCARDED', updated_at=now()"
            + " where department_id=? and id=? and status='PREVIEWED'",
        department(), batchId);
    if (changed != 1) {
      if (jdbc.queryForObject("select count(*) from csv_import_batches where department_id=? and id=?",
          Integer.class, department(), batchId) == 0) throw ApiException.missing();
      throw ApiException.conflict("NOT_DISCARDABLE", "This import was already decided.");
    }
    audit.log("CSV_IMPORT_DISCARDED", "CsvImportBatch", batchId, "UI", Map.of());
    return summary(batchId);
  }

  public List<Map<String, Object>> batches(int page, int size) {
    admin();
    return jdbc.queryForList("""
        select id, entity_type as "entityType", file_name as "fileName", status,
          duplicate_policy as "duplicatePolicy", total_rows as "totalRows",
          created_at as "createdAt", committed_at as "committedAt"
        from csv_import_batches where department_id=?
        order by created_at desc, id desc limit ? offset ?
        """, department(), Math.max(1, size), Math.max(0, page) * Math.max(1, size));
  }

  public Map<String, Object> detail(long batchId) {
    admin();
    var batch = jdbc.queryForList("""
        select id, entity_type as "entityType", file_name as "fileName", status,
          duplicate_policy as "duplicatePolicy", total_rows as "totalRows",
          created_at as "createdAt", committed_at as "committedAt"
        from csv_import_batches where department_id=? and id=?
        """, department(), batchId);
    if (batch.isEmpty()) throw ApiException.missing();
    var result = new LinkedHashMap<String, Object>(batch.getFirst());
    result.put("rows", jdbc.queryForList("""
        select id, row_number as "rowNumber", raw_row as "rawRow", status, problems,
          created_record_id as "createdRecordId"
        from csv_import_rows where batch_id=? order by row_number
        """, batchId));
    result.put("ready", count(batchId, "READY"));
    result.put("skippedDuplicates", count(batchId, "SKIPPED_DUPLICATE"));
    result.put("invalid", count(batchId, "INVALID"));
    return result;
  }

  private int count(long batchId, String status) {
    return jdbc.queryForObject(
        "select count(*) from csv_import_rows where batch_id=? and status=?",
        Integer.class, batchId, status);
  }

  private Map<String, Object> summary(long batchId) {
    return detail(batchId);
  }

  private record RowOutcome(Map<String, Object> input, List<String> problems, boolean duplicate) {}

  private RowOutcome validateRow(String type, List<String> cells, String policy) {
    List<String> expected = HEADERS.get(type);
    List<String> problems = new ArrayList<>();
    if (cells.size() != expected.size()) {
      problems.add("Expected " + expected.size() + " columns, found " + cells.size() + ".");
      return new RowOutcome(null, problems, false);
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < expected.size(); i++) values.put(expected.get(i), cells.get(i).trim());

    Map<String, Object> input = new LinkedHashMap<>();
    switch (type) {
      case "PATIENT" -> {
        requireText(values, "patientIdentifier", 64, problems);
        requireText(values, "firstName", 100, problems);
        requireText(values, "lastName", 100, problems);
        LocalDate dob = requireDate(values, "dateOfBirth", problems);
        if (dob != null && !dob.isBefore(LocalDate.now()))
          problems.add("dateOfBirth must be in the past.");
        if (values.get("address") != null && values.get("address").length() > 500)
          problems.add("address is longer than 500 characters.");
        String phone = values.getOrDefault("phoneNumber", "");
        if (!phone.isBlank() && !phone.matches("\\+[1-9]\\d{7,14}"))
          problems.add("phoneNumber must be E.164, for example +359****3456.");
        input.put("patientIdentifier", values.get("patientIdentifier"));
        input.put("firstName", values.get("firstName"));
        input.put("lastName", values.get("lastName"));
        input.put("dateOfBirth", dob == null ? null : dob.toString());
        input.put("address", values.get("address"));
        input.put("phoneNumber", phone.isBlank() ? null : phone);
      }
      case "DOCTOR" -> {
        requireText(values, "doctorIdentifier", 64, problems);
        requireText(values, "firstName", 100, problems);
        requireText(values, "lastName", 100, problems);
        requireText(values, "specialty", 100, problems);
        input.putAll(values);
      }
      case "ROOM" -> {
        requireText(values, "roomNumber", 30, problems);
        int beds = 0;
        try {
          beds = Integer.parseInt(values.getOrDefault("bedCount", ""));
        } catch (NumberFormatException ignored) {
          problems.add("bedCount must be a whole number.");
        }
        if (values.getOrDefault("bedCount", "").matches("\\d+") && (beds < 1 || beds > 100))
          problems.add("bedCount must be between 1 and 100.");
        input.put("roomNumber", values.get("roomNumber"));
        input.put("bedCount", beds);
        input.put("active", true);
        input.put("capabilities", List.of());
        input.put("version", 0);
      }
      case "PROCEDURE" -> {
        requireText(values, "procedureCode", 64, problems);
        requireText(values, "procedureName", 150, problems);
        BigDecimal cost = null;
        String rawCost = values.getOrDefault("currentCost", "");
        if (rawCost.isBlank()) problems.add("currentCost is required.");
        else {
          try {
            cost = new BigDecimal(rawCost);
            if (cost.signum() < 0) problems.add("currentCost cannot be negative.");
            if (cost.scale() > 2) problems.add("currentCost allows at most two decimal places.");
          } catch (NumberFormatException e) {
            problems.add("currentCost must be a number.");
          }
        }
        input.put("procedureCode", values.get("procedureCode"));
        input.put("procedureName", values.get("procedureName"));
        input.put("currentCost", cost == null ? null : cost.toPlainString());
        input.put("active", true);
        input.put("version", 0);
      }
      default -> problems.add("Unsupported entity type.");
    }

    if (!problems.isEmpty()) return new RowOutcome(null, problems, false);
    if (isDuplicate(type, values)) {
      // SKIP leaves the existing record alone and reports the row; REJECT blocks the
      // row outright. Falling through to READY here would let a duplicate through
      // under REJECT and only surface later as a constraint violation.
      if ("REJECT".equals(policy))
        return new RowOutcome(null,
            List.of("Matches an existing record; this batch rejects duplicates."), false);
      return new RowOutcome(input, List.of(), true);
    }
    return new RowOutcome(input, List.of(), false);
  }

  private void requireText(Map<String, String> values, String field, int max, List<String> problems) {
    String value = values.getOrDefault(field, "");
    if (value.isBlank()) problems.add(field + " is required.");
    else if (value.length() > max) problems.add(field + " is longer than " + max + " characters.");
  }

  private LocalDate requireDate(Map<String, String> values, String field, List<String> problems) {
    String value = values.getOrDefault(field, "");
    if (value.isBlank()) {
      problems.add(field + " is required.");
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      problems.add(field + " must be an ISO date, for example 1980-01-31.");
      return null;
    }
  }

  private boolean isDuplicate(String type, Map<String, String> values) {
    return switch (type) {
      case "PATIENT" -> Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*)>0 from patients where department_id=? and patient_identifier=?",
          Boolean.class, department(), values.get("patientIdentifier")));
      case "DOCTOR" -> Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*)>0 from doctors where department_id=? and doctor_identifier=?",
          Boolean.class, department(), values.get("doctorIdentifier")));
      case "ROOM" -> Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*)>0 from rooms where department_id=? and room_number=?",
          Boolean.class, department(), values.get("roomNumber")));
      default -> Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*)>0 from medical_procedures where department_id=? and procedure_code=?",
          Boolean.class, department(), values.get("procedureCode")));
    };
  }

  private Long createRecord(String type, Object parsed) {
    if (!(parsed instanceof String text)) return null;
    Map<String, Object> m;
    try {
      m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Map.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Stored row is not readable JSON", e);
    }
    return switch (type) {
      case "PATIENT" -> patients.save(null, new PatientInput(
          str(m, "patientIdentifier"), str(m, "firstName"), str(m, "lastName"),
          LocalDate.parse(str(m, "dateOfBirth")), str(m, "address"),
          str(m, "phoneNumber"), null)).getId();
      case "DOCTOR" -> catalogue.saveDoctor(null, new DoctorInput(
          str(m, "doctorIdentifier"), str(m, "firstName"), str(m, "lastName"),
          str(m, "specialty"), true, 0L)).getId();
      case "ROOM" -> catalogue.saveRoom(null, new RoomInput(
          str(m, "roomNumber"), ((Number) m.get("bedCount")).intValue(), true,
          List.of(), 0L)).getId();
      default -> catalogue.saveProcedure(null, new ProcedureInput(
          str(m, "procedureCode"), str(m, "procedureName"),
          new BigDecimal(str(m, "currentCost")), true, 0L)).getId();
    };
  }

  private static String str(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? null : String.valueOf(value);
  }

  /**
   * The staged row is stored as JSON text. This is deliberate: the PostgreSQL
   * driver is a runtime-scope dependency here, so a jsonb column would need
   * PGobject and a pom change for no benefit. The batch is internal and short
   * lived, and nothing queries into this column.
   */
  private String json(Map<String, Object> value) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not serialise the parsed row", e);
    }
  }

  /** Minimal RFC 4180 splitting: quoted fields, doubled quotes, no embedded newlines. */
  static List<String> splitCsvLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
          else quoted = false;
        } else cell.append(c);
      } else if (c == '"') quoted = true;
      else if (c == ',') { out.add(cell.toString()); cell.setLength(0); }
      else cell.append(c);
    }
    out.add(cell.toString());
    return out;
  }
}
