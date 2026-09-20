package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "admissions")
public class Admission extends DepartmentEntity {
  private String admissionNumber;
  private Long patientId;
  private Long attendingDoctorId;
  private java.time.Instant admissionDateTime;
  private java.time.Instant dischargeDateTime;
  private java.time.LocalDate expectedDischargeDate;
  private String status = "ACTIVE";
  private Long createdBy;

  public String getAdmissionNumber() {
    return admissionNumber;
  }

  public void setAdmissionNumber(String admissionNumber) {
    this.admissionNumber = admissionNumber;
  }

  public Long getPatientId() {
    return patientId;
  }

  public void setPatientId(Long patientId) {
    this.patientId = patientId;
  }

  public Long getAttendingDoctorId() {
    return attendingDoctorId;
  }

  public void setAttendingDoctorId(Long attendingDoctorId) {
    this.attendingDoctorId = attendingDoctorId;
  }

  public java.time.Instant getAdmissionDateTime() {
    return admissionDateTime;
  }

  public void setAdmissionDateTime(java.time.Instant admissionDateTime) {
    this.admissionDateTime = admissionDateTime;
  }

  public java.time.Instant getDischargeDateTime() {
    return dischargeDateTime;
  }

  public void setDischargeDateTime(java.time.Instant dischargeDateTime) {
    this.dischargeDateTime = dischargeDateTime;
  }

  public java.time.LocalDate getExpectedDischargeDate() {
    return expectedDischargeDate;
  }

  public void setExpectedDischargeDate(java.time.LocalDate expectedDischargeDate) {
    this.expectedDischargeDate = expectedDischargeDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }
}
