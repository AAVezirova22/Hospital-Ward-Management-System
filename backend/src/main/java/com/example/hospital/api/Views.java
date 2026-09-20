package com.example.hospital.api;

import com.example.hospital.domain.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Views {
  private Views() {}

  public static Map<String, Object> PatientDirectory(Patient p) {
    return PatientDirectory.of(p);
  }

  public record PatientDirectory(
      Long id, String patientIdentifier, String firstName, String lastName, LocalDate dateOfBirth) {
    public static Map<String, Object> of(Patient p) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", p.id);
      row.put("patientIdentifier", p.patientIdentifier);
      row.put("firstName", p.firstName);
      row.put("lastName", p.lastName);
      row.put("dateOfBirth", p.dateOfBirth);
      return row;
    }
  }

  public static Map<String, Object> patient(Patient p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.id);
    row.put("version", p.version);
    row.put("patientIdentifier", p.patientIdentifier);
    row.put("firstName", p.firstName);
    row.put("lastName", p.lastName);
    row.put("dateOfBirth", p.dateOfBirth);
    row.put("address", p.address);
    row.put("phoneNumber", p.phoneNumber);
    return row;
  }

  public static Map<String, Object> doctor(Doctor d) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", d.id);
    row.put("version", d.version);
    row.put("doctorIdentifier", d.doctorIdentifier);
    row.put("firstName", d.firstName);
    row.put("lastName", d.lastName);
    row.put("specialty", d.specialty);
    row.put("active", d.active);
    return row;
  }
}
