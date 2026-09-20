package com.example.hospital.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "performed_procedures")
public class PerformedProcedure extends DepartmentEntity {
  private Long admissionId;
  private Long medicalProcedureId;
  private Long performedByDoctorId;
  private Instant performedAt;
  private String note;
  private BigDecimal priceAtExecution;

  public Long getAdmissionId() { return admissionId; }
  public void setAdmissionId(Long admissionId) { this.admissionId = admissionId; }
  public Long getMedicalProcedureId() { return medicalProcedureId; }
  public void setMedicalProcedureId(Long medicalProcedureId) { this.medicalProcedureId = medicalProcedureId; }
  public Long getPerformedByDoctorId() { return performedByDoctorId; }
  public void setPerformedByDoctorId(Long performedByDoctorId) { this.performedByDoctorId = performedByDoctorId; }
  public Instant getPerformedAt() { return performedAt; }
  public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public BigDecimal getPriceAtExecution() { return priceAtExecution; }
  public void setPriceAtExecution(BigDecimal priceAtExecution) { this.priceAtExecution = priceAtExecution; }
}
