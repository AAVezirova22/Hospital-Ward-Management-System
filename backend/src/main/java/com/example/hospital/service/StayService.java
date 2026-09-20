package com.example.hospital.service;

import com.example.hospital.api.AdmissionInput;
import com.example.hospital.api.ApiException;
import com.example.hospital.api.DischargeInput;
import com.example.hospital.api.RecordProcedureInput;
import com.example.hospital.api.TransferInput;
import com.example.hospital.api.Views;
import com.example.hospital.domain.Admission;
import com.example.hospital.domain.Doctor;
import com.example.hospital.domain.PerformedProcedure;
import com.example.hospital.domain.Room;
import com.example.hospital.domain.RoomAssignment;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.PerformedProcedureRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admission lifecycle: admission, transfer, discharge, attending doctor and recorded procedures. */
@Service
@Transactional(readOnly = true)
public class StayService {
  private final HospitalService hospital;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final PerformedProcedureRepository performed;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AuditService audit;

  public StayService(
      HospitalService hospital,
      AdmissionRepository admissions,
      RoomAssignmentRepository assignments,
      PerformedProcedureRepository performed,
      WorkflowLockRepository lock,
      Actor actor,
      AuditService audit) {
    this.hospital = hospital;
    this.admissions = admissions;
    this.assignments = assignments;
    this.performed = performed;
    this.lock = lock;
    this.actor = actor;
    this.audit = audit;
  }

  public List<Admission> list() {
    return hospital.admissions();
  }

  public Map<String, Object> view(Long id) {
    return view(hospital.admission(id));
  }

  /** Full dossier for one admission: patient, attending doctor, bed history, procedures and cost. */
  public Map<String, Object> view(Admission a) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("admission", Views.admission(a));
    v.put("patient", Views.patient(hospital.patient(a.getPatientId())));
    v.put("doctor", Views.doctor(hospital.doctor(a.getAttendingDoctorId())));
    v.put(
        "assignment",
        Views.assignment(assignments.findByAdmissionIdAndReleasedAtIsNull(a.getId()).orElse(null)));
    v.put(
        "rooms",
        assignments.findByAdmissionIdOrderByAssignedAt(a.getId()).stream()
            .map(
                ra ->
                    Map.of(
                        "assignment",
                        Views.assignment(ra),
                        "room",
                        Views.room(hospital.room(ra.getRoomId()))))
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
                        Views.procedure(hospital.procedure(pp.getMedicalProcedureId())),
                        "doctor",
                        Views.doctor(hospital.doctor(pp.getPerformedByDoctorId()))))
            .toList());
    v.put(
        "totalCost",
        ps.stream().map(pp -> pp.getPriceAtExecution()).reduce(BigDecimal.ZERO, BigDecimal::add));
    return v;
  }

  @Transactional
  public Object admit(AdmissionInput in) {
    return Views.admission(admit(in, "UI"));
  }

  @Transactional
  public Admission admit(AdmissionInput in, String source) {
    lock.acquire();
    actor.staff();
    hospital.patient(in.patientId());
    activeDoctor(in.doctorId());
    freeRoom(in.roomId());
    if (admissions.findByPatientIdAndStatus(in.patientId(), "ACTIVE").isPresent())
      throw ApiException.conflict(
          "ALREADY_ADMITTED", "The patient already has an active admission.");
    var a = new Admission();
    a.setPatientId(in.patientId());
    a.setAttendingDoctorId(in.doctorId());
    a.setAdmissionDateTime(Instant.now());
    a.setAdmissionNumber("ADM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    a.setCreatedBy(actor.user().getId());
    admissions.saveAndFlush(a);
    assign(a, in.roomId(), "Admission", source);
    audit.log("ADMISSION_CREATED", "Admission", a.getId(), source);
    return a;
  }

  @Transactional
  public Object transfer(Long id, TransferInput in) {
    return Views.admission(transfer(id, in, "UI"));
  }

  @Transactional
  public Admission transfer(Long id, TransferInput in, String source) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, in.version());
    var ra =
        assignments.findByAdmissionIdAndReleasedAtIsNull(id).orElseThrow(ApiException::missing);
    if (ra.getRoomId().equals(in.roomId()))
      throw ApiException.conflict("SAME_ROOM", "The patient is already in that room.");
    freeRoom(in.roomId());
    ra.setReleasedAt(Instant.now());
    assignments.saveAndFlush(ra);
    assign(a, in.roomId(), in.reason(), source);
    a.setUpdatedAt(Instant.now());
    admissions.saveAndFlush(a);
    audit.log("ROOM_TRANSFERRED", "Admission", a.getId(), source);
    return a;
  }

  @Transactional
  public Object discharge(Long id, DischargeInput in) {
    return Views.admission(discharge(id, in.version(), "UI"));
  }

  @Transactional
  public Admission discharge(Long id, Long v, String source) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, v);
    a.setStatus("DISCHARGED");
    a.setDischargeDateTime(Instant.now());
    var ra =
        assignments.findByAdmissionIdAndReleasedAtIsNull(id).orElseThrow(ApiException::missing);
    ra.setReleasedAt(a.getDischargeDateTime());
    assignments.save(ra);
    admissions.saveAndFlush(a);
    audit.log("PATIENT_DISCHARGED", "Admission", a.getId(), source);
    return a;
  }

  @Transactional
  public Object changeDoctor(Long id, Long doctorId, Long version) {
    return Views.admission(assignDoctor(id, doctorId, version));
  }

  @Transactional
  public Admission assignDoctor(Long id, Long doctorId, Long v) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, v);
    activeDoctor(doctorId);
    a.setAttendingDoctorId(doctorId);
    admissions.saveAndFlush(a);
    audit.log("DOCTOR_ASSIGNED", "Admission", a.getId(), "UI");
    return a;
  }

  @Transactional
  public Object recordProcedure(Long id, RecordProcedureInput in) {
    return Views.performed(performProcedure(id, in));
  }

  @Transactional
  public PerformedProcedure performProcedure(Long id, RecordProcedureInput in) {
    lock.acquire();
    var a = hospital.admission(id);
    active(a);
    activeDoctor(in.doctorId());
    if (actor.doctor() && !actor.user().getDoctorId().equals(in.doctorId()))
      throw new AccessDeniedException("Cannot record for another doctor");
    if (in.performedAt().isBefore(a.getAdmissionDateTime()) || in.performedAt().isAfter(Instant.now()))
      throw new ApiException(
          400, "INVALID_PROCEDURE_TIME", "Procedure time must fall within the active admission.");
    var mp = hospital.procedure(in.medicalProcedureId());
    if (!mp.isActive())
      throw ApiException.conflict("PROCEDURE_INACTIVE", "Select an active procedure.");
    var p = new PerformedProcedure();
    p.setAdmissionId(id);
    p.setMedicalProcedureId(mp.getId());
    p.setPerformedByDoctorId(in.doctorId());
    p.setPerformedAt(in.performedAt());
    p.setNote(in.note());
    p.setPriceAtExecution(mp.getCurrentCost());
    performed.saveAndFlush(p);
    audit.log("PROCEDURE_RECORDED", "PerformedProcedure", p.getId(), "UI");
    return p;
  }

  private Doctor activeDoctor(Long id) {
    var d = hospital.doctor(id);
    if (!d.isActive()) throw ApiException.conflict("DOCTOR_INACTIVE", "Choose an active doctor.");
    return d;
  }

  private Room freeRoom(Long id) {
    var r = hospital.room(id);
    if (!r.isActive() || hospital.occupied(id) >= r.getBedCount())
      throw ApiException.conflict(
          "ROOM_CAPACITY_EXCEEDED", "Room " + r.getRoomNumber() + " no longer has available capacity.");
    return r;
  }

  private void active(Admission a) {
    if (!a.getStatus().equals("ACTIVE"))
      throw ApiException.conflict("ADMISSION_CLOSED", "This admission is already closed.");
  }

  private void assign(Admission a, Long roomId, String reason, String source) {
    var ra = new RoomAssignment();
    ra.setAdmissionId(a.getId());
    ra.setRoomId(roomId);
    ra.setAssignedAt(Instant.now());
    ra.setReason(reason);
    ra.setCreatedBy(actor.user().getId());
    assignments.save(ra);
    audit.log("ROOM_ASSIGNED", "Admission", a.getId(), source);
  }
}
