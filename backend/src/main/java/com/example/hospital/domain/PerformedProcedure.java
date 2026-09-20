package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "performed_procedures")
public class PerformedProcedure extends DepartmentEntity {
  private Long admissionId;
  private Long medicalProcedureId;
  private Long performedByDoctorId;
  private java.time.Instant performedAt;
  private String note;
  private java.math.BigDecimal priceAtExecution;

  public Long getAdmissionId() {
    return admissionId;
  }

  public void setAdmissionId(Long admissionId) {
    this.admissionId = admissionId;
  }

  public Long getMedicalProcedureId() {
    return medicalProcedureId;
  }

  public void setMedicalProcedureId(Long medicalProcedureId) {
    this.medicalProcedureId = medicalProcedureId;
  }

  public Long getPerformedByDoctorId() {
    return performedByDoctorId;
  }

  public void setPerformedByDoctorId(Long performedByDoctorId) {
    this.performedByDoctorId = performedByDoctorId;
  }

  public java.time.Instant getPerformedAt() {
    return performedAt;
  }

  public void setPerformedAt(java.time.Instant performedAt) {
    this.performedAt = performedAt;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public java.math.BigDecimal getPriceAtExecution() {
    return priceAtExecution;
  }

  public void setPriceAtExecution(java.math.BigDecimal priceAtExecution) {
    this.priceAtExecution = priceAtExecution;
  }
}
