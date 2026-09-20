package com.example.hospital.service;

import com.example.hospital.api.Inputs.PatientInput;
import com.example.hospital.domain.Patient;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PatientService {
  private final HospitalService hospital;

  public PatientService(HospitalService hospital) {
    this.hospital = hospital;
  }

  public List<Patient> list(String search) {
    return hospital.patients(search);
  }

  public Patient byRef(String ref) {
    return hospital.patientByRef(ref);
  }

  public Map<String, Object> summary(String ref) {
    return hospital.summary(ref);
  }

  public Patient save(Long id, PatientInput in) {
    return hospital.savePatient(id, in);
  }
}
