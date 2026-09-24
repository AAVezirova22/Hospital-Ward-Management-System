package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.PatientInput;
import com.example.hospital.domain.Patient;
import com.example.hospital.repository.PatientRepository;
import com.example.hospital.security.Actor;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {
  private final HospitalService hospital;
  private final PatientRepository patients;
  private final Actor actor;
  private final AuditService audit;

  public PatientService(
      HospitalService hospital, PatientRepository patients, Actor actor, AuditService audit) {
    this.hospital = hospital;
    this.patients = patients;
    this.actor = actor;
    this.audit = audit;
  }

  public Page<Patient> list(String search, int page, int size) {
    return hospital.patients(search, page, size);
  }

  public Page<Patient> list(
      String search, Boolean activeAdmission, Long doctorId, Long roomId, int page, int size) {
    if ((doctorId != null && doctorId < 1) || (roomId != null && roomId < 1))
      throw new ApiException(
          400, "VALIDATION_ERROR", "Doctor and room filters must use positive identifiers.");
    return hospital.patientDirectory(search, activeAdmission, doctorId, roomId, page, size);
  }

  public Patient byRef(String ref) {
    return hospital.patientByRef(ref);
  }

  public Map<String, Object> summary(String ref) {
    var summary = hospital.summary(ref);
    var patient = (Map<?, ?>) summary.get("patient");
    audit.read("PATIENT_VIEWED", "Patient", ((Number) patient.get("id")).longValue(), "UI");
    return summary;
  }

  @Transactional
  public Patient save(Long id, PatientInput in) {
    actor.staff();
    var p = id == null ? new Patient() : hospital.patient(id);
    if (id != null) HospitalService.version(p, in.version());
    p.setPatientIdentifier(in.patientIdentifier().trim());
    p.setFirstName(in.firstName().trim());
    p.setLastName(in.lastName().trim());
    p.setDateOfBirth(in.dateOfBirth());
    p.setAddress(in.address());
    p.setPhoneNumber(in.phoneNumber() == null || in.phoneNumber().isBlank() ? null : in.phoneNumber().trim());
    patients.saveAndFlush(p);
    audit.log(id == null ? "PATIENT_CREATED" : "PATIENT_UPDATED", "Patient", p.getId(), "UI");
    return p;
  }
}
