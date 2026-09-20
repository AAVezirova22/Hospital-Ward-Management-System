package com.example.hospital.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_lock")
public class WorkflowLock {
  @Id public Long id;
}
