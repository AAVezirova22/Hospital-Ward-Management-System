package com.example.hospital.service;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
  private final BedHoldRepository bedHolds;
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
      BedHoldRepository bh,
      MedicalProcedureRepository mp,
      PerformedProcedureRepository pp,
      Actor actor,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    patients = p;
    doctors = d;
    rooms = r;
    admissions = a;
    assignments = ra;
    bedHolds = bh;
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

  public Page<Patient> patients(String search, int page, int size) {
    return patientDirectory(search, null, null, null, page, size);
  }

  public Page<Patient> patientDirectory(
      String search, Boolean activeAdmission, Long doctorId, Long roomId, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable = PageRequest.of(safePage, safeSize);
    String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    Long scopedDoctorId = actor.doctor() ? actor.user().getDoctorId() : null;
    Long departmentId = com.example.hospital.security.DepartmentContext.id();
    Page<Patient> result =
        patients.findDirectory(
            query, activeAdmission, doctorId, roomId, departmentId, scopedDoctorId, pageable);
    if (result.getTotalPages() > 0 && safePage >= result.getTotalPages()) {
      return patients.findDirectory(
          query,
          activeAdmission,
          doctorId,
          roomId,
          departmentId,
          scopedDoctorId,
          PageRequest.of(result.getTotalPages() - 1, safeSize));
    }
    return result;
  }

  public List<Patient> patientMatches(String search, int limit) {
    return patients(search, 0, limit).getContent();
  }

  public Patient patientByRef(String ref) {
    if (ref == null || ref.isBlank()) throw ApiException.missing();
    if (ref.chars().allMatch(Character::isDigit)) return patient(Long.parseLong(ref));
    var found = patients.findByDepartmentIdAndPatientIdentifier(
        com.example.hospital.security.DepartmentContext.id(), ref).orElseThrow(ApiException::missing);
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
    var currentActor = actor.user();
    boolean doctorScoped = currentActor.getRole().equals("DOCTOR");
    Long scopedDoctorId = doctorScoped ? currentActor.getDoctorId() : null;
    boolean patientScoped = currentActor.getRole().equals("PATIENT");
    Long scopedPatientId = patientScoped ? currentActor.getPatientId() : null;
    return admissions.findVisibleAdmissions(
        DepartmentContext.id(),
        doctorScoped,
        scopedDoctorId,
        patientScoped,
        scopedPatientId,
        Sort.by(Sort.Order.desc("admissionDateTime")).and(Sort.by(Sort.Order.desc("id"))));
  }

  public List<Admission> admissions(
      String status,
      Instant fromDate,
      Instant toDateExclusive,
      Long doctorId,
      Pageable pageable) {
    var currentActor = actor.user();
    boolean doctorScoped = currentActor.getRole().equals("DOCTOR");
    Long scopedDoctorId = doctorScoped ? currentActor.getDoctorId() : null;
    boolean patientScoped = currentActor.getRole().equals("PATIENT");
    Long scopedPatientId = patientScoped ? currentActor.getPatientId() : null;
    return admissions.searchAdmissions(
        DepartmentContext.id(),
        status != null,
        status,
        fromDate != null,
        fromDate,
        toDateExclusive != null,
        toDateExclusive,
        doctorId != null,
        doctorId,
        doctorScoped,
        scopedDoctorId,
        patientScoped,
        scopedPatientId,
        pageable);
  }

  public long admissionCount(
      String status, Instant fromDate, Instant toDateExclusive, Long doctorId) {
    var currentActor = actor.user();
    boolean doctorScoped = currentActor.getRole().equals("DOCTOR");
    Long scopedDoctorId = doctorScoped ? currentActor.getDoctorId() : null;
    boolean patientScoped = currentActor.getRole().equals("PATIENT");
    Long scopedPatientId = patientScoped ? currentActor.getPatientId() : null;
    return admissions.countSearchAdmissions(
        DepartmentContext.id(),
        status != null,
        status,
        fromDate != null,
        fromDate,
        toDateExclusive != null,
        toDateExclusive,
        doctorId != null,
        doctorId,
        doctorScoped,
        scopedDoctorId,
        patientScoped,
        scopedPatientId);
  }

  public Room room(Long id) {
    return rooms.findById(id).orElseThrow(ApiException::missing);
  }

  public long occupied(Long id) {
    return assignments.countByRoomIdAndReleasedAtIsNull(id);
  }

  public int held(Long id) {
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var holds = bedHolds.findByRoomIdAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(id, now);
    return BedHoldCapacity.reserved(holds, now);
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
    if (minFree < 0 || minFree > 100)
      throw new ApiException(
          400, "VALIDATION_ERROR", "Minimum available beds must be between 0 and 100.");
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var holdsByRoom =
        bedHolds.findByCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(now).stream()
            .collect(java.util.stream.Collectors.groupingBy(BedHold::getRoomId));
    return rooms.findAll().stream()
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>(Views.room(r));
              long used = occupied(r.getId());
              var holds = holdsByRoom.getOrDefault(r.getId(), List.of());
              int reserved = BedHoldCapacity.reserved(holds, now);
              m.put("occupiedBeds", used);
              m.put("heldBeds", reserved);
              m.put("activeHeldBeds", BedHoldCapacity.active(holds, now));
              m.put("holds", holds.stream().map(Views::bedHold).toList());
              m.put("availableBeds", r.isActive() ? Math.max(0, r.getBedCount() - (int) used - reserved) : 0);
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
