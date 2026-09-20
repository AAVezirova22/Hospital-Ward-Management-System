package com.example.hospital.service;

import com.example.hospital.api.DoctorInput;
import com.example.hospital.api.ProcedureInput;
import com.example.hospital.api.RoomInput;
import com.example.hospital.domain.Doctor;
import com.example.hospital.domain.MedicalProcedure;
import com.example.hospital.domain.Room;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CatalogueService {
  private final HospitalService hospital;

  public CatalogueService(HospitalService hospital) {
    this.hospital = hospital;
  }

  public List<Doctor> doctors() {
    return hospital.doctors();
  }

  public Doctor saveDoctor(Long id, DoctorInput in) {
    return hospital.saveDoctor(id, in);
  }

  public List<Map<String, Object>> rooms(int minFree) {
    return hospital.rooms(minFree);
  }

  public Room saveRoom(Long id, RoomInput in) {
    return hospital.saveRoom(id, in);
  }

  public List<MedicalProcedure> procedures() {
    return hospital.procedures();
  }

  public MedicalProcedure saveProcedure(Long id, ProcedureInput in) {
    return hospital.saveProcedure(id, in);
  }
}
