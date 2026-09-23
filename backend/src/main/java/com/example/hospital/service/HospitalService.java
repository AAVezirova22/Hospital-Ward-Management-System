package com.example.hospital.service;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HospitalService {
  private final PatientRepository patients;
  private final DoctorRepository doctors;
  private final RoomRepository rooms;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final MedicalProcedureRepository catalogue;
  private final PerformedProcedureRepository performed;
  private final Actor actor;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public HospitalService(
      PatientRepository p,
      DoctorRepository d,
      RoomRepository r,
      AdmissionRepository a,
      RoomAssignmentRepository ra,
      MedicalProcedureRepository mp,
      PerformedProcedureRepository pp,
      Actor actor,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    patients = p;
    doctors = d;
    rooms = r;
    admissions = a;
    assignments = ra;
    catalogue = mp;
    performed = pp;
    this.actor = actor;
    this.jdbc = jdbc;
  }

  public void accessible(Long patientId) {
    if (actor.user().getRole().equals("PATIENT") && !Objects.equals(actor.user().getPatientId(), patientId))
      throw new AccessDeniedException("This patient record is not yours");
    if (actor.doctor()
        && !admissions.existsByPatientIdAndAttendingDoctorId(patientId, actor.user().getDoctorId()))
      throw new AccessDeniedException("Patient not assigned to this doctor");
  }

  public boolean visible(Admission a) {
    if (actor.user().getRole().equals("PATIENT")) return Objects.equals(a.getPatientId(), actor.user().getPatientId());
    return !actor.doctor() || Objects.equals(a.getAttendingDoctorId(), actor.user().getDoctorId());
  }

  public Patient patient(Long id) {
    accessible(id);
    return patients.findById(id).orElseThrow(ApiException::missing);
  }

  public List<Patient> patients(String search) {
    var q = search == null ? "" : search.toLowerCase(Locale.ROOT);
    return patients.findAll().stream()
        .filter(
            p ->
                (p.getFirstName() + " " + p.getLastName() + " " + p.getPatientIdentifier())
                    .toLowerCase(Locale.ROOT)
                    .contains(q))
        .filter(
            p ->
                !actor.doctor()
                    || admissions.existsByPatientIdAndAttendingDoctorId(
                        p.getId(), actor.user().getDoctorId()))
        .sorted(Comparator.comparing(p -> p.getLastName()))
        .toList();
  }

  public Patient patientByRef(String ref) {
    if (ref == null || ref.isBlank()) throw ApiException.missing();
    if (ref.chars().allMatch(Character::isDigit)) return patient(Long.parseLong(ref));
    var found =
        patients.findAll().stream()
            .filter(p -> p.getPatientIdentifier().equals(ref))
            .findFirst()
            .orElseThrow(ApiException::missing);
    accessible(found.getId());
    return found;
  }

  public Map<String, Object> summary(String ref) {
    return summary(patientByRef(ref).getId());
  }

  public List<Doctor> doctors() {
    return doctors.findAll();
  }

  public List<MedicalProcedure> procedures() {
    return catalogue.findAll();
  }

  public Admission admission(Long id) {
    var a = admissions.findById(id).orElseThrow(ApiException::missing);
    if (!visible(a)) throw new AccessDeniedException("Admission not assigned");
    return a;
  }

  public List<Admission> admissions() {
    return admissions.findAll().stream()
        .filter(this::visible)
        .sorted(Comparator.comparing((Admission a) -> a.getAdmissionDateTime()).reversed())
        .toList();
  }

  public Room room(Long id) {
    return rooms.findById(id).orElseThrow(ApiException::missing);
  }

  public long occupied(Long id) {
    return assignments.countByRoomIdAndReleasedAtIsNull(id);
  }

  public Long roomIdForAdmission(Long admissionId) {
    return assignments.findByAdmissionIdAndReleasedAtIsNull(admissionId)
        .map(RoomAssignment::getRoomId)
        .orElseThrow(ApiException::missing);
  }

  public String scopeLabel() {
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var rows =
        jdbc.queryForList(
            "select h.name as hospital, d.name as department from departments d join hospitals h on h.id=d.hospital_id where d.id=?",
            departmentId);
    if (rows.isEmpty()) return "Department " + departmentId;
    return rows.getFirst().get("hospital") + " / " + rows.getFirst().get("department");
  }

  public List<Map<String, Object>> rooms(int minFree) {
    return rooms(minFree, List.of());
  }

  public List<Map<String, Object>> rooms(
      int minFree, java.util.Collection<String> requiredCapabilities) {
    if (minFree < 0 || minFree > 100)
      throw new ApiException(
          400, "VALIDATION_ERROR", "Minimum available beds must be between 0 and 100.");
    var required = RoomCapabilityMatcher.normalize(requiredCapabilities);
    return rooms.findAll().stream()
        .filter(r -> RoomCapabilityMatcher.missing(required, r.getCapabilities()).isEmpty())
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>(Views.room(r));
              long used = occupied(r.getId());
              m.put("occupiedBeds", used);
              m.put("availableBeds", r.isActive() ? r.getBedCount() - used : 0);
              return m;
            })
        .filter(m -> ((Number) m.get("availableBeds")).intValue() >= minFree)
        .toList();
  }

  public Map<String, Object> admissionView(Admission a) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("admission", Views.admission(a));
    v.put("patient", Views.patient(patient(a.getPatientId())));
    v.put("doctor", Views.doctor(doctors.findById(a.getAttendingDoctorId()).orElseThrow()));
    v.put("assignment", Views.assignment(assignments.findByAdmissionIdAndReleasedAtIsNull(a.getId()).orElse(null)));
    v.put(
        "rooms",
        assignments.findByAdmissionIdOrderByAssignedAt(a.getId()).stream()
            .map(ra -> Map.of("assignment", Views.assignment(ra), "room", Views.room(room(ra.getRoomId()))))
            .toList());
    var ps = performed.findByAdmissionIdOrderByPerformedAtDesc(a.getId());
    v.put(
        "procedures",
        ps.stream()
            .map(
                pp ->
                    Map.of(
                        "record",
                        Views.performed(pp),
                        "procedure",
                        Views.procedure(catalogue.findById(pp.getMedicalProcedureId()).orElseThrow()),
                        "doctor",
                        Views.doctor(doctors.findById(pp.getPerformedByDoctorId()).orElseThrow())))
            .toList());
    v.put(
        "totalCost",
        ps.stream().map(pp -> pp.getPriceAtExecution()).reduce(BigDecimal.ZERO, BigDecimal::add));
    return v;
  }

  public Map<String, Object> summary(Long id) {
    var p = patient(id);
    return Map.of(
        "patient",
        Views.patient(p),
        "admissions",
        admissions.findByPatientIdOrderByAdmissionDateTimeDesc(id).stream()
            .filter(this::visible)
            .map(this::admissionView)
            .toList());
  }

  public static void version(BaseEntity e, Long v) {
    if (v == null || e.getVersion() != v)
      throw ApiException.conflict("STALE_STATE", "This record changed. Refresh before continuing.");
  }
}
