package com.example.hospital.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
  private final HospitalService hospital;

  public ReportService(HospitalService hospital) {
    this.hospital = hospital;
  }

  public Map<String, Object> dashboard() {
    return hospital.dashboard();
  }

  public List<Map<String, Object>> census(Long roomId, Long doctorId) {
    return hospital.census(roomId, doctorId);
  }

  public Map<String, Object> procedures(LocalDate from, LocalDate to, Long patientId, Long doctorId) {
    return hospital.procedureReport(from, to, patientId, doctorId);
  }
}
