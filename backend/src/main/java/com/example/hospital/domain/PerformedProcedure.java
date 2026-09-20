package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "performed_procedures")
public class PerformedProcedure extends DepartmentEntity {
  public Long admissionId;
  public Long medicalProcedureId;
  public Long performedByDoctorId;
  public java.time.Instant performedAt;
  public String note;
  public java.math.BigDecimal priceAtExecution;
}
