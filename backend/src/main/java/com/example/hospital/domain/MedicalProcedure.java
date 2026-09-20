package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "medical_procedures")
public class MedicalProcedure extends DepartmentEntity {
  private String procedureCode;
  private String procedureName;
  private java.math.BigDecimal currentCost;
  private boolean active = true;

  public String getProcedureCode() {
    return procedureCode;
  }

  public void setProcedureCode(String procedureCode) {
    this.procedureCode = procedureCode;
  }

  public String getProcedureName() {
    return procedureName;
  }

  public void setProcedureName(String procedureName) {
    this.procedureName = procedureName;
  }

  public java.math.BigDecimal getCurrentCost() {
    return currentCost;
  }

  public void setCurrentCost(java.math.BigDecimal currentCost) {
    this.currentCost = currentCost;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
