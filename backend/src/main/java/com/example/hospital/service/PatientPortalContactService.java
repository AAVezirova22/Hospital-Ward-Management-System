package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientPortalContactService {
  public record ContactInput(String address, String phoneNumber) {}

  private static final Pattern PHONE = Pattern.compile("[+0-9().\\-\\s]{3,40}");
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;

  public PatientPortalContactService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> update(ContactInput input) {
    if (!"PATIENT".equals(actor.user().getRole()))
      throw new org.springframework.security.access.AccessDeniedException("Patient account required");
    if (input == null || (input.address() == null && input.phoneNumber() == null))
      throw new ApiException(400, "VALIDATION_ERROR", "Enter an address or phone number to update.");

    Long patientId = actor.user().getPatientId();
    if (patientId == null) throw new ApiException(404, "PATIENT_NOT_FOUND", "Your patient record is unavailable.");
    Map<String, Object> current;
    try {
      current = jdbc.queryForMap("select id, department_id, address, phone_number as \"phoneNumber\" from patients where id=? for update", patientId);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new ApiException(404, "PATIENT_NOT_FOUND", "Your patient record is unavailable.");
    }
    long departmentId = ((Number) current.get("department_id")).longValue();
    String address = input.address() == null ? (String) current.get("address") : clean(input.address(), 500, "Address");
    String phone = input.phoneNumber() == null ? (String) current.get("phoneNumber") : clean(input.phoneNumber(), 40, "Phone number");
    if (phone != null && !PHONE.matcher(phone).matches())
      throw new ApiException(400, "VALIDATION_ERROR", "Enter a valid phone number using digits and common phone punctuation.");

    ArrayList<String> changed = new ArrayList<>();
    if (input.address() != null && !java.util.Objects.equals(address, current.get("address"))) changed.add("address");
    if (input.phoneNumber() != null && !java.util.Objects.equals(phone, current.get("phoneNumber"))) changed.add("phoneNumber");
    if (!changed.isEmpty()) {
      int updated = jdbc.update("update patients set address=?, phone_number=?, version=version+1, updated_at=now() where id=? and department_id=?",
          address, phone, patientId, departmentId);
      if (updated != 1) throw new ApiException(404, "PATIENT_NOT_FOUND", "Your patient record is unavailable.");
      audit.logForDepartment(departmentId, "PATIENT_CONTACT_UPDATED", "Patient", patientId, "PORTAL",
          Map.of("patientId", patientId, "fields", changed));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("address", address);
    result.put("phoneNumber", phone);
    return result;
  }

  private static String clean(String value, int max, String label) {
    if (value == null || value.isBlank()) return null;
    String result = value.strip();
    if (result.length() > max) throw new ApiException(400, "VALIDATION_ERROR", label + " is too long.");
    return result;
  }
}
