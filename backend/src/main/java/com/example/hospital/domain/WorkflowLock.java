package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_lock")
public class WorkflowLock {
  @Id private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
