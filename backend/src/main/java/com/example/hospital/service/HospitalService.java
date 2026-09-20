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
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AuditService audit;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public HospitalService(
      PatientRepository p,
      DoctorRepository d,
      RoomRepository r,
      AdmissionRepository a,
      RoomAssignmentRepository ra,
      MedicalProcedureRepository mp,
      PerformedProcedureRepository pp,
      WorkflowLockRepository l,
      Actor actor,
      AuditService audit,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    patients = p;
    doctors = d;
    rooms = r;
    admissions = a;
    assignments = ra;
    catalogue = mp;
    performed = pp;
    lock = l;
    this.actor = actor;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  public void accessible(Long patientId) {
    if (actor.user().role.equals("PATIENT") && !Objects.equals(actor.user().patientId, patientId))
      throw new AccessDeniedException("This patient record is not yours");
    if (actor.doctor()
        && !admissions.existsByPatientIdAndAttendingDoctorId(patientId, actor.user().doctorId))
      throw new AccessDeniedException("Patient not assigned to this doctor");
  }

  public boolean visible(Admission a) {
    if (actor.user().role.equals("PATIENT")) return Objects.equals(a.patientId, actor.user().patientId);
    return !actor.doctor() || Objects.equals(a.attendingDoctorId, actor.user().doctorId);
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
                (p.firstName + " " + p.lastName + " " + p.patientIdentifier)
                    .toLowerCase(Locale.ROOT)
                    .contains(q))
        .filter(
            p ->
                !actor.doctor()
                    || admissions.existsByPatientIdAndAttendingDoctorId(
                        p.id, actor.user().doctorId))
        .sorted(Comparator.comparing(p -> p.lastName))
        .toList();
  }

  public Patient patientByRef(String ref) {
    if (ref == null || ref.isBlank()) throw ApiException.missing();
    if (ref.chars().allMatch(Character::isDigit)) return patient(Long.parseLong(ref));
    var found =
        patients.findAll().stream()
            .filter(p -> p.patientIdentifier.equals(ref))
            .findFirst()
            .orElseThrow(ApiException::missing);
    accessible(found.id);
    return found;
  }

  public Map<String, Object> summary(String ref) {
    return summary(patientByRef(ref).id);
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
        .sorted(Comparator.comparing((Admission a) -> a.admissionDateTime).reversed())
        .toList();
  }

  public Room room(Long id) {
    return rooms.findById(id).orElseThrow(ApiException::missing);
  }

  public long occupied(Long id) {
    return assignments.countByRoomIdAndReleasedAtIsNull(id);
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
    return rooms.findAll().stream()
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", r.id);
              m.put("roomNumber", r.roomNumber);
              m.put("bedCount", r.bedCount);
              m.put("active", r.active);
              m.put("version", r.version);
              m.put("occupiedBeds", occupied(r.id));
              m.put("availableBeds", r.active ? r.bedCount - occupied(r.id) : 0);
              return m;
            })
        .filter(m -> ((Number) m.get("availableBeds")).intValue() >= minFree)
        .toList();
  }

  public Map<String, Object> admissionView(Admission a) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("admission", Views.admission(a));
    v.put("patient", Views.patient(patient(a.patientId)));
    v.put("doctor", Views.doctor(doctors.findById(a.attendingDoctorId).orElseThrow()));
    v.put("assignment", assignments.findByAdmissionIdAndReleasedAtIsNull(a.id).orElse(null));
    v.put(
        "rooms",
        assignments.findByAdmissionIdOrderByAssignedAt(a.id).stream()
            .map(ra -> Map.of("assignment", ra, "room", room(ra.roomId)))
            .toList());
    var ps = performed.findByAdmissionIdOrderByPerformedAtDesc(a.id);
    v.put(
        "procedures",
        ps.stream()
            .map(
                pp ->
                    Map.of(
                        "record",
                        Views.performed(pp),
                        "procedure",
                        Views.procedure(catalogue.findById(pp.medicalProcedureId).orElseThrow()),
                        "doctor",
                        Views.doctor(doctors.findById(pp.performedByDoctorId).orElseThrow()))
            .toList());
    v.put(
        "totalCost",
        ps.stream().map(pp -> pp.priceAtExecution).reduce(BigDecimal.ZERO, BigDecimal::add));
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
    if (v == null || e.version != v)
      throw ApiException.conflict("STALE_STATE", "This record changed. Refresh before continuing.");
  }

  @Transactional
  public Patient savePatient(Long id, PatientInput in) {
    actor.staff();
    var p = id == null ? new Patient() : patient(id);
    if (id != null) version(p, in.version());
    p.patientIdentifier = in.patientIdentifier().trim();
    p.firstName = in.firstName().trim();
    p.lastName = in.lastName().trim();
    p.dateOfBirth = in.dateOfBirth();
    p.address = in.address();
    p.phoneNumber = in.phoneNumber() == null || in.phoneNumber().isBlank() ? null : in.phoneNumber().trim();
    patients.saveAndFlush(p);
    audit.log(id == null ? "PATIENT_CREATED" : "PATIENT_UPDATED", "Patient", p.id, "UI");
    return p;
  }

  @Transactional
  public Doctor saveDoctor(Long id, DoctorInput in) {
    lock.acquire();
    actor.admin();
    var d = id == null ? new Doctor() : doctors.findById(id).orElseThrow(ApiException::missing);
    if (id != null) version(d, in.version());
    if (!in.active() && id != null && admissions.existsByAttendingDoctorIdAndStatus(id, "ACTIVE"))
      throw ApiException.conflict(
          "DOCTOR_HAS_PATIENTS", "Reassign active admissions before deactivating this doctor.");
    d.doctorIdentifier = in.doctorIdentifier().trim();
    d.firstName = in.firstName().trim();
    d.lastName = in.lastName().trim();
    d.specialty = in.specialty().trim();
    d.active = in.active();
    doctors.saveAndFlush(d);
    audit.log("DOCTOR_SAVED", "Doctor", d.id, "UI");
    return d;
  }

  @Transactional
  public Room saveRoom(Long id, RoomInput in) {
    lock.acquire();
    actor.admin();
    var r = id == null ? new Room() : room(id);
    if (id != null) version(r, in.version());
    long used = id == null ? 0 : occupied(id);
    if (in.bedCount() < used || (!in.active() && used > 0))
      throw ApiException.conflict(
          "ROOM_OCCUPIED",
          "The room has occupied beds; transfer patients before reducing capacity or deactivating"
              + " it.");
    r.roomNumber = in.roomNumber().trim();
    r.bedCount = in.bedCount();
    r.active = in.active();
    rooms.saveAndFlush(r);
    audit.log("ROOM_SAVED", "Room", r.id, "UI");
    return r;
  }

  @Transactional
  public MedicalProcedure saveProcedure(Long id, ProcedureInput in) {
    actor.admin();
    var p =
        id == null
            ? new MedicalProcedure()
            : catalogue.findById(id).orElseThrow(ApiException::missing);
    if (id != null) version(p, in.version());
    p.procedureCode = in.procedureCode().trim();
    p.procedureName = in.procedureName().trim();
    p.currentCost = in.currentCost();
    p.active = in.active();
    catalogue.saveAndFlush(p);
    audit.log("PROCEDURE_SAVED", "MedicalProcedure", p.id, "UI");
    return p;
  }

  private Doctor activeDoctor(Long id) {
    var d = doctors.findById(id).orElseThrow(ApiException::missing);
    if (!d.active) throw ApiException.conflict("DOCTOR_INACTIVE", "Choose an active doctor.");
    return d;
  }

  private Room freeRoom(Long id) {
    var r = room(id);
    if (!r.active || occupied(id) >= r.bedCount)
      throw ApiException.conflict(
          "ROOM_CAPACITY_EXCEEDED", "Room " + r.roomNumber + " no longer has available capacity.");
    return r;
  }

  private void active(Admission a) {
    if (!a.status.equals("ACTIVE"))
      throw ApiException.conflict("ADMISSION_CLOSED", "This admission is already closed.");
  }

  private void assign(Admission a, Long roomId, String reason, String source) {
    var ra = new RoomAssignment();
    ra.admissionId = a.id;
    ra.roomId = roomId;
    ra.assignedAt = Instant.now();
    ra.reason = reason;
    ra.createdBy = actor.user().id;
    assignments.save(ra);
    audit.log("ROOM_ASSIGNED", "Admission", a.id, source);
  }

  @Transactional
  public Admission admit(AdmissionInput in, String source) {
    lock.acquire();
    actor.staff();
    patient(in.patientId());
    activeDoctor(in.doctorId());
    freeRoom(in.roomId());
    if (admissions.findByPatientIdAndStatus(in.patientId(), "ACTIVE").isPresent())
      throw ApiException.conflict(
          "ALREADY_ADMITTED", "The patient already has an active admission.");
    var a = new Admission();
    a.patientId = in.patientId();
    a.attendingDoctorId = in.doctorId();
    a.admissionDateTime = Instant.now();
    a.admissionNumber = "ADM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    a.createdBy = actor.user().id;
    admissions.saveAndFlush(a);
    assign(a, in.roomId(), "Admission", source);
    audit.log("ADMISSION_CREATED", "Admission", a.id, source);
    return a;
  }

  @Transactional
  public Admission transfer(Long id, TransferInput in, String source) {
    lock.acquire();
    actor.staff();
    var a = admission(id);
    active(a);
    version(a, in.version());
    var ra =
        assignments.findByAdmissionIdAndReleasedAtIsNull(id).orElseThrow(ApiException::missing);
    if (ra.roomId.equals(in.roomId()))
      throw ApiException.conflict("SAME_ROOM", "The patient is already in that room.");
    freeRoom(in.roomId());
    ra.releasedAt = Instant.now();
    assignments.saveAndFlush(ra);
    assign(a, in.roomId(), in.reason(), source);
    a.updatedAt = Instant.now();
    admissions.saveAndFlush(a);
    audit.log("ROOM_TRANSFERRED", "Admission", a.id, source);
    return a;
  }

  @Transactional
  public Admission discharge(Long id, Long v, String source) {
    lock.acquire();
    actor.staff();
    var a = admission(id);
    active(a);
    version(a, v);
    a.status = "DISCHARGED";
    a.dischargeDateTime = Instant.now();
    var ra =
        assignments.findByAdmissionIdAndReleasedAtIsNull(id).orElseThrow(ApiException::missing);
    ra.releasedAt = a.dischargeDateTime;
    assignments.save(ra);
    admissions.saveAndFlush(a);
    audit.log("PATIENT_DISCHARGED", "Admission", a.id, source);
    return a;
  }

  @Transactional
  public Admission changeDoctor(Long id, Long doctorId, Long v) {
    lock.acquire();
    actor.staff();
    var a = admission(id);
    active(a);
    version(a, v);
    activeDoctor(doctorId);
    a.attendingDoctorId = doctorId;
    admissions.saveAndFlush(a);
    audit.log("DOCTOR_ASSIGNED", "Admission", a.id, "UI");
    return a;
  }

  @Transactional
  public PerformedProcedure recordProcedure(Long id, RecordProcedureInput in) {
    lock.acquire();
    var a = admission(id);
    active(a);
    activeDoctor(in.doctorId());
    if (actor.doctor() && !actor.user().doctorId.equals(in.doctorId()))
      throw new AccessDeniedException("Cannot record for another doctor");
    if (in.performedAt().isBefore(a.admissionDateTime) || in.performedAt().isAfter(Instant.now()))
      throw new ApiException(
          400, "INVALID_PROCEDURE_TIME", "Procedure time must fall within the active admission.");
    var mp = catalogue.findById(in.medicalProcedureId()).orElseThrow(ApiException::missing);
    if (!mp.active)
      throw ApiException.conflict("PROCEDURE_INACTIVE", "Select an active procedure.");
    var p = new PerformedProcedure();
    p.admissionId = id;
    p.medicalProcedureId = mp.id;
    p.performedByDoctorId = in.doctorId();
    p.performedAt = in.performedAt();
    p.note = in.note();
    p.priceAtExecution = mp.currentCost;
    performed.saveAndFlush(p);
    audit.log("PROCEDURE_RECORDED", "PerformedProcedure", p.id, "UI");
    return p;
  }

  public Map<String, Object> dashboard() {
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    Long doctorId = actor.doctor() ? actor.user().doctorId : null;
    Long activeAdmissions =
        doctorId == null
            ? jdbc.queryForObject(
                "select count(*) from admissions where department_id=? and status='ACTIVE'",
                Long.class,
                departmentId)
            : jdbc.queryForObject(
                "select count(*) from admissions where department_id=? and status='ACTIVE' and attending_doctor_id=?",
                Long.class,
                departmentId,
                doctorId);
    Long occupiedBeds =
        jdbc.queryForObject(
            "select count(*) from room_assignments ra join rooms r on r.id=ra.room_id where r.department_id=? and ra.released_at is null",
            Long.class,
            departmentId);
    Long totalBeds =
        jdbc.queryForObject(
            "select coalesce(sum(bed_count),0) from rooms where department_id=? and active=true",
            Long.class,
            departmentId);
    Long availableBeds =
        jdbc.queryForObject(
            """
            select coalesce(sum(r.bed_count),0) - (
              select count(*) from room_assignments ra join rooms x on x.id=ra.room_id
              where x.department_id=? and ra.released_at is null and x.active=true)
            from rooms r where r.department_id=? and r.active=true
            """,
            Long.class,
            departmentId,
            departmentId);
    Long activeDoctors =
        jdbc.queryForObject(
            "select count(*) from doctors where department_id=? and active=true",
            Long.class,
            departmentId);
    var start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Long proceduresToday =
        doctorId == null
            ? jdbc.queryForObject(
                "select count(*) from performed_procedures where department_id=? and performed_at>=? and performed_at<?",
                Long.class,
                departmentId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end))
            : jdbc.queryForObject(
                "select count(*) from performed_procedures pp join admissions a on a.id=pp.admission_id where pp.department_id=? and pp.performed_at>=? and pp.performed_at<? and a.attending_doctor_id=?",
                Long.class,
                departmentId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end),
                doctorId);
    return Map.of(
        "activeAdmissions",
        activeAdmissions == null ? 0 : activeAdmissions,
        "occupiedBeds",
        occupiedBeds == null ? 0 : occupiedBeds,
        "totalBeds",
        totalBeds == null ? 0 : totalBeds,
        "availableBeds",
        availableBeds == null ? 0 : availableBeds,
        "activeDoctors",
        activeDoctors == null ? 0 : activeDoctors,
        "proceduresToday",
        proceduresToday == null ? 0 : proceduresToday,
        "scope",
        actor.doctor() ? "Your assigned admissions; department bed capacity" : "Department");
  }

  public Map<String, Object> procedureReport(
      LocalDate from, LocalDate to, Long patientId, Long doctorId) {
    if (from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before end date.");
    if (patientId != null) accessible(patientId);
    var start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var allowed =
        admissions().stream()
            .filter(a -> patientId == null || a.patientId.equals(patientId))
            .map(a -> a.id)
            .collect(java.util.stream.Collectors.toSet());
    var ps =
        performed.findAll().stream()
            .filter(
                p ->
                    allowed.contains(p.admissionId)
                        && !p.performedAt.isBefore(start)
                        && p.performedAt.isBefore(end)
                        && (doctorId == null || p.performedByDoctorId.equals(doctorId)))
            .toList();
    var rows =
        ps.stream()
            .map(
                p -> {
                  var a = admissions.findById(p.admissionId).orElseThrow();
                  return Map.of(
                      "record",
                      Views.performed(p),
                      "patient",
                      Views.patient(patient(a.patientId)),
                      "doctor",
                      Views.doctor(doctors.findById(p.performedByDoctorId).orElseThrow()),
                      "procedure",
                      Views.procedure(catalogue.findById(p.medicalProcedureId).orElseThrow()));
                })
            .toList();
    Map<Long, BigDecimal> grouped = new LinkedHashMap<>();
    ps.forEach(p -> grouped.merge(p.performedByDoctorId, p.priceAtExecution, BigDecimal::add));
    return Map.of(
        "rows",
        rows,
        "totalCost",
        ps.stream().map(p -> p.priceAtExecution).reduce(BigDecimal.ZERO, BigDecimal::add),
        "byDoctor",
        grouped,
        "from",
        from,
        "to",
        to);
  }

  public List<Map<String, Object>> census(Long roomId, Long doctorId) {
    return admissions().stream()
        .filter(
            a ->
                a.status.equals("ACTIVE")
                    && (doctorId == null || a.attendingDoctorId.equals(doctorId)))
        .filter(
            a ->
                roomId == null
                    || assignments
                        .findByAdmissionIdAndReleasedAtIsNull(a.id)
                        .map(ra -> ra.roomId.equals(roomId))
                        .orElse(false))
        .map(this::admissionView)
        .toList();
  }
}
