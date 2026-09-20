package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.Views;
import com.example.hospital.api.AdmissionInput;
import com.example.hospital.api.DischargeInput;
import com.example.hospital.api.RecordProcedureInput;
import com.example.hospital.api.TransferInput;
import com.example.hospital.domain.Admission;
import com.example.hospital.domain.Doctor;
import com.example.hospital.domain.PerformedProcedure;
import com.example.hospital.domain.Room;
import com.example.hospital.domain.RoomAssignment;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.DoctorRepository;
import com.example.hospital.repository.MedicalProcedureRepository;
import com.example.hospital.repository.PerformedProcedureRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StayService {
  private final HospitalService hospital;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final DoctorRepository doctors;
  private final MedicalProcedureRepository catalogue;
  private final PerformedProcedureRepository performed;
  private final AuditService audit;

  public StayService(
      HospitalService hospital,
      WorkflowLockRepository lock,
      Actor actor,
      AdmissionRepository admissions,
      RoomAssignmentRepository assignments,
      DoctorRepository doctors,
      MedicalProcedureRepository catalogue,
      PerformedProcedureRepository performed,
      AuditService audit) {
    this.hospital = hospital;
    this.lock = lock;
    this.actor = actor;
    this.admissions = admissions;
    this.assignments = assignments;
    this.doctors = doctors;
    this.catalogue = catalogue;
    this.performed = performed;
    this.audit = audit;
  }

  public List<Admission> list() {
    return hospital.admissions();
  }

  public Map<String, Object> view(Admission admission) {
    return hospital.admissionView(admission);
  }

  public Map<String, Object> view(Long id) {
    return hospital.admissionView(hospital.admission(id));
  }

  public Object admit(AdmissionInput in) {
    return Views.admission(create(in, "UI"));
  }

  public Object transfer(Long id, TransferInput in) {
    return Views.admission(move(id, in, "UI"));
  }

  public Object discharge(Long id, DischargeInput in) {
    return Views.admission(close(id, in.version(), "UI"));
  }

  public Object changeDoctor(Long id, Long doctorId, Long version) {
    return Views.admission(reassign(id, doctorId, version));
  }

  public Object recordProcedure(Long id, RecordProcedureInput in) {
    return Views.performed(record(id, in));
  }

  private Doctor activeDoctor(Long id) {
    var d = doctors.findById(id).orElseThrow(ApiException::missing);
    if (!d.active) throw ApiException.conflict("DOCTOR_INACTIVE", "Choose an active doctor.");
    return d;
  }

  private Room freeRoom(Long id) {
    var r = hospital.room(id);
    if (!r.active || hospital.occupied(id) >= r.bedCount)
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
  public Admission create(AdmissionInput in, String source) {
    lock.acquire();
    actor.staff();
    hospital.patient(in.patientId());
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
  public Admission move(Long id, TransferInput in, String source) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, in.version());
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
  public Admission close(Long id, Long v, String source) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, v);
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
  public Admission reassign(Long id, Long doctorId, Long v) {
    lock.acquire();
    actor.staff();
    var a = hospital.admission(id);
    active(a);
    HospitalService.version(a, v);
    activeDoctor(doctorId);
    a.attendingDoctorId = doctorId;
    admissions.saveAndFlush(a);
    audit.log("DOCTOR_ASSIGNED", "Admission", a.id, "UI");
    return a;
  }

  @Transactional
  public PerformedProcedure record(Long id, RecordProcedureInput in) {
    lock.acquire();
    var a = hospital.admission(id);
    active(a);
    activeDoctor(in.doctorId());
    if (actor.doctor() && !actor.user().doctorId.equals(in.doctorId()))
      throw new AccessDeniedException("Cannot record for another doctor");
    if (in.performedAt().isBefore(a.admissionDateTime) || in.performedAt().isAfter(Instant.now()))
      throw new ApiException(
          400, "INVALID_PROCEDURE_TIME", "Procedure time must fall within the active admission.");
    var mp = catalogue.findById(in.medicalProcedureId()).orElseThrow(ApiException::missing);
    if (!mp.active) throw ApiException.conflict("PROCEDURE_INACTIVE", "Select an active procedure.");
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
}
