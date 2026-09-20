package com.example.hospital.domain;

import com.example.hospital.security.DepartmentContext;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@MappedSuperclass
@FilterDef(name = "department", defaultCondition = "department_id = :departmentId",
    autoEnabled = true, applyToLoadByKey = true,
    parameters = @ParamDef(name = "departmentId", type = Long.class, resolver = DepartmentContext.class))
@Filter(name = "department")
public abstract class DepartmentEntity extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private Long departmentId;

  @PrePersist
  void scope() { departmentId = DepartmentContext.id(); }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }
}
