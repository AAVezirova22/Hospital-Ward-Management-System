package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.DoctorInput;
import com.example.hospital.api.ProcedureInput;
import com.example.hospital.api.RoomInput;
import com.example.hospital.domain.Doctor;
import com.example.hospital.domain.MedicalProcedure;
import com.example.hospital.domain.Room;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.DoctorRepository;
import com.example.hospital.repository.MedicalProcedureRepository;
import com.example.hospital.repository.RoomRepository;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogueService {
  private final HospitalService hospital;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final DoctorRepository doctors;
  private final RoomRepository rooms;
  private final MedicalProcedureRepository catalogue;
  private final AdmissionRepository admissions;
  private final AuditService audit;

  public CatalogueService(
      HospitalService hospital,
      WorkflowLockRepository lock,
      Actor actor,
      DoctorRepository doctors,
      RoomRepository rooms,
      MedicalProcedureRepository catalogue,
      AdmissionRepository admissions,
      AuditService audit) {
    this.hospital = hospital;
    this.lock = lock;
    this.actor = actor;
    this.doctors = doctors;
    this.rooms = rooms;
    this.catalogue = catalogue;
    this.admissions = admissions;
    this.audit = audit;
  }

  public List<Doctor> doctors() {
    return hospital.doctors();
  }

  public List<Map<String, Object>> rooms(int minFree) {
    return hospital.rooms(minFree);
  }

  public List<MedicalProcedure> procedures() {
    return hospital.procedures();
  }

  @Transactional
  public Doctor saveDoctor(Long id, DoctorInput in) {
    lock.acquire();
    actor.admin();
    var d = id == null ? new Doctor() : doctors.findById(id).orElseThrow(ApiException::missing);
    if (id != null) HospitalService.version(d, in.version());
    if (!in.active() && id != null && admissions.existsByAttendingDoctorIdAndStatus(id, "ACTIVE"))
      throw ApiException.conflict(
          "DOCTOR_HAS_PATIENTS", "Reassign active admissions before deactivating this doctor.");
    d.setDoctorIdentifier(in.doctorIdentifier().trim());
    d.setFirstName(in.firstName().trim());
    d.setLastName(in.lastName().trim());
    d.setSpecialty(in.specialty().trim());
    d.setActive(in.active());
    doctors.saveAndFlush(d);
    audit.log("DOCTOR_SAVED", "Doctor", d.getId(), "UI");
    return d;
  }

  @Transactional
  public Room saveRoom(Long id, RoomInput in) {
    lock.acquire();
    actor.admin();
    var r = id == null ? new Room() : hospital.room(id);
    if (id != null) HospitalService.version(r, in.version());
    long used = id == null ? 0 : hospital.occupied(id);
    if (in.bedCount() < used || (!in.active() && used > 0))
      throw ApiException.conflict(
          "ROOM_OCCUPIED",
          "The room has occupied beds; transfer patients before reducing capacity or deactivating"
              + " it.");
    r.setRoomNumber(in.roomNumber().trim());
    r.setBedCount(in.bedCount());
    r.setActive(in.active());
    rooms.saveAndFlush(r);
    audit.log("ROOM_SAVED", "Room", r.getId(), "UI");
    return r;
  }

  @Transactional
  public MedicalProcedure saveProcedure(Long id, ProcedureInput in) {
    actor.admin();
    var p =
        id == null
            ? new MedicalProcedure()
            : catalogue.findById(id).orElseThrow(ApiException::missing);
    if (id != null) HospitalService.version(p, in.version());
    p.setProcedureCode(in.procedureCode().trim());
    p.setProcedureName(in.procedureName().trim());
    p.setCurrentCost(in.currentCost());
    p.setActive(in.active());
    catalogue.saveAndFlush(p);
    audit.log("PROCEDURE_SAVED", "MedicalProcedure", p.getId(), "UI");
    return p;
  }
}
