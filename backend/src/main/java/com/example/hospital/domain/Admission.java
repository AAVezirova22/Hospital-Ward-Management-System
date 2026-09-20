package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "admissions")
public class Admission extends BaseEntity {
  public String admissionNumber;
  public Long patientId;
  public Long attendingDoctorId;
  public java.time.Instant admissionDateTime;
  public java.time.Instant dischargeDateTime;
  public java.time.LocalDate expectedDischargeDate;
  public String status = "ACTIVE";
  public Long createdBy;
}
