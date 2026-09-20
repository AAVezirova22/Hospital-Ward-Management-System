package com.example.hospital.service;

import com.example.hospital.api.PatientInput;
import com.example.hospital.api.Views;
import com.example.hospital.domain.Patient;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.PatientRepository;
import com.example.hospital.security.Actor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Patient directory records and the operational dossier assembled from their admissions. */
@Service
@Transactional(readOnly = true)
public class PatientService {
  private final HospitalService hospital;
  private final StayService stays;
  private final PatientRepository patients;
  private final AdmissionRepository admissions;
  private final Actor actor;
  private final AuditService audit;

  public PatientService(
      HospitalService hospital,
      StayService stays,
      PatientRepository patients,
      AdmissionRepository admissions,
      Actor actor,
      AuditService audit) {
    this.hospital = hospital;
    this.stays = stays;
    this.patients = patients;
    this.admissions = admissions;
    this.actor = actor;
    this.audit = audit;
  }

  public List<Patient> list(String search) {
    return hospital.patients(search);
  }

  public Patient byRef(String ref) {
    return hospital.patientByRef(ref);
  }

  public Map<String, Object> summary(String ref) {
    return summary(hospital.patientByRef(ref).getId());
  }

  public Map<String, Object> summary(Long id) {
    var p = hospital.patient(id);
    return Map.of(
        "patient",
        Views.patient(p),
        "admissions",
        admissions.findByPatientIdOrderByAdmissionDateTimeDesc(id).stream()
            .filter(hospital::visible)
            .map(stays::view)
            .toList());
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
