package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "medical_procedures")
public class MedicalProcedure extends BaseEntity {
  public String procedureCode;
  public String procedureName;
  public java.math.BigDecimal currentCost;
  public boolean active = true;
}
