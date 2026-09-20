package com.example.hospital.service;

import com.example.hospital.api.Views;
import com.example.hospital.api.AdmissionInput;
import com.example.hospital.api.DischargeInput;
import com.example.hospital.api.RecordProcedureInput;
import com.example.hospital.api.TransferInput;
import com.example.hospital.domain.Admission;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StayService {
  private final HospitalService hospital;

  public StayService(HospitalService hospital) {
    this.hospital = hospital;
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
    return Views.admission(hospital.admit(in, "UI"));
  }

  public Object transfer(Long id, TransferInput in) {
    return Views.admission(hospital.transfer(id, in, "UI"));
  }

  public Object discharge(Long id, DischargeInput in) {
    return Views.admission(hospital.discharge(id, in.version(), "UI"));
  }

  public Object changeDoctor(Long id, Long doctorId, Long version) {
    return Views.admission(hospital.changeDoctor(id, doctorId, version));
  }

  public Object recordProcedure(Long id, RecordProcedureInput in) {
    return hospital.recordProcedure(id, in);
  }
}
