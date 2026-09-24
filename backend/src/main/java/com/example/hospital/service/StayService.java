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
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Collection;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StayService {
  private static final int MAX_PAGE_SIZE = 100;
  private static final Set<String> ADMISSION_STATUSES = Set.of("ACTIVE", "DISCHARGED", "CANCELLED");

  private final HospitalService hospital;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final DoctorRepository doctors;
  private final MedicalProcedureRepository catalogue;
  private final PerformedProcedureRepository performed;
  private final AuditService audit;
  private final CareWorkflowService careWorkflows;

  public StayService(
      HospitalService hospital,
      WorkflowLockRepository lock,
      Actor actor,
      AdmissionRepository admissions,
      RoomAssignmentRepository assignments,
      DoctorRepository doctors,
      MedicalProcedureRepository catalogue,
      PerformedProcedureRepository performed,
      AuditService audit,
      CareWorkflowService careWorkflows) {
    this.hospital = hospital;
    this.lock = lock;
    this.actor = actor;
    this.admissions = admissions;
    this.assignments = assignments;
    this.doctors = doctors;
    this.catalogue = catalogue;
    this.performed = performed;
    this.audit = audit;
    this.careWorkflows = careWorkflows;
  }

  public List<Admission> list() {
    return hospital.admissions();
  }

  public Page<Admission> list(
      int page, int size, String status, LocalDate from, LocalDate to, Long doctorId) {
    if (page < 0 || size < 1 || (doctorId != null && doctorId < 1))
      throw new ApiException(
          400, "VALIDATION_ERROR", "Check the admission filters and page values.");
    if (from != null && to != null && from.isAfter(to))
      throw new ApiException(
          400, "VALIDATION_ERROR", "The start date must not be after the end date.");

    String normalizedStatus =
        status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    if (normalizedStatus != null && !ADMISSION_STATUSES.contains(normalizedStatus))
      throw new ApiException(400, "VALIDATION_ERROR", "Choose a valid admission status.");

    Instant fromDate = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toDateExclusive = null;
    if (to != null) {
      try {
        toDateExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeException e) {
        throw new ApiException(400, "VALIDATION_ERROR", "The end date is outside the supported range.");
      }
    }

    int pageSize = Math.min(size, MAX_PAGE_SIZE);
    Sort sort =
        Sort.by(Sort.Order.desc("admissionDateTime")).and(Sort.by(Sort.Order.desc("id")));
    return page(
        page,
        pageSize,
        sort,
        normalizedStatus,
        fromDate,
        toDateExclusive,
        doctorId);
  }

  private Page<Admission> page(
      int page,
      int size,
      Sort sort,
      String status,
      Instant fromDate,
      Instant toDateExclusive,
      Long doctorId) {
    long totalElements = hospital.admissionCount(status, fromDate, toDateExclusive, doctorId);
    long lastPageNumber = totalElements == 0 ? 0 : (totalElements - 1) / size;
    int effectivePage = (int) Math.min(page, Math.min(lastPageNumber, Integer.MAX_VALUE));
    Pageable pageable = PageRequest.of(effectivePage, size, sort);
    List<Admission> content =
        hospital.admissions(status, fromDate, toDateExclusive, doctorId, pageable);
    return new PageImpl<>(content, pageable, totalElements);
  }

  public Map<String, Object> view(Admission admission) {
    return hospital.admissionView(admission);
  }

  public Map<String, Object> view(Long id) {
    return hospital.admissionView(hospital.admission(id));
  }

  @Transactional
  public Object admit(AdmissionInput in) {
    return Views.admission(create(in, "UI"));
  }

  @Transactional
  public Object transfer(Long id, TransferInput in) {
    return Views.admission(move(id, in, "UI"));
  }

  @Transactional
  public Object discharge(Long id, DischargeInput in) {
    return Views.admission(close(id, in.version(), "UI"));
  }

  @Transactional
  public Object changeDoctor(Long id, Long doctorId, Long version) {
    return Views.admission(reassign(id, doctorId, version));
  }

  @Transactional
  public Object recordProcedure(Long id, RecordProcedureInput in) {
    return Views.performed(record(id, in));
  }

  private Doctor activeDoctor(Long id) {
    var d = doctors.findById(id).orElseThrow(ApiException::missing);
    if (!d.isActive()) throw ApiException.conflict("DOCTOR_INACTIVE", "Choose an active doctor.");
    return d;
  }

  private Room freeRoom(Long id, Collection<String> requiredCapabilities) {
    var r = hospital.room(id);
if (!r.isActive()) {
  throw ApiException.conflict(
      "ROOM_INACTIVE", "Room " + r.getRoomNumber() + " is inactive.");
}

RoomCapabilityMatcher.require(r, requiredCapabilities);

if (hospital.occupied(id) + hospital.held(id) >= r.getBedCount())
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

  @Transactional
  public Admission create(AdmissionInput in, String source) {
    lock.acquire();
    actor.staff();
    hospital.patient(in.patientId());
    activeDoctor(in.doctorId());
    var requirements = RoomCapabilityMatcher.normalize(in.requiredRoomCapabilities());
    freeRoom(in.roomId(), requirements);
    if (admissions.findByPatientIdAndStatus(in.patientId(), "ACTIVE").isPresent())
      throw ApiException.conflict(
          "ALREADY_ADMITTED", "The patient already has an active admission.");
    var a = new Admission();
    a.setPatientId(in.patientId());
    a.setAttendingDoctorId(in.doctorId());
    a.setAdmissionDateTime(Instant.now());
    a.setAdmissionNumber("ADM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    a.setCreatedBy(actor.user().getId());
    a.setRequiredRoomCapabilities(requirements);
    admissions.saveAndFlush(a);
    assign(a, in.roomId(), "Admission", source);
    audit.log("ADMISSION_CREATED", "Admission", a.getId(), source);
    careWorkflows.launchForTrigger(a.getId(), "ADMISSION");
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
    if (ra.getRoomId().equals(in.roomId()))
      throw ApiException.conflict("SAME_ROOM", "The patient is already in that room.");
    freeRoom(in.roomId(), a.getRequiredRoomCapabilities());
    ra.setReleasedAt(Instant.now());
    assignments.saveAndFlush(ra);
    assign(a, in.roomId(), in.reason(), source);
    a.setUpdatedAt(Instant.now());
    admissions.saveAndFlush(a);
    audit.log("ROOM_TRANSFERRED", "Admission", a.getId(), source);
    return a;
  }

  @Transactional
  public Admission close(Long id, Long v, String source) {
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
    careWorkflows.launchForTrigger(a.getId(), "DISCHARGE");
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
    a.setAttendingDoctorId(doctorId);
    admissions.saveAndFlush(a);
    audit.log("DOCTOR_ASSIGNED", "Admission", a.getId(), "UI");
    return a;
  }

  @Transactional
  public PerformedProcedure record(Long id, RecordProcedureInput in) {
    lock.acquire();
    var a = hospital.admission(id);
    active(a);
    activeDoctor(in.doctorId());
    if (actor.doctor() && !actor.user().getDoctorId().equals(in.doctorId()))
      throw new AccessDeniedException("Cannot record for another doctor");
    if (in.performedAt().isBefore(a.getAdmissionDateTime()) || in.performedAt().isAfter(Instant.now()))
      throw new ApiException(
          400, "INVALID_PROCEDURE_TIME", "Procedure time must fall within the active admission.");
    var mp = catalogue.findById(in.medicalProcedureId()).orElseThrow(ApiException::missing);
    if (!mp.isActive()) throw ApiException.conflict("PROCEDURE_INACTIVE", "Select an active procedure.");
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
}
