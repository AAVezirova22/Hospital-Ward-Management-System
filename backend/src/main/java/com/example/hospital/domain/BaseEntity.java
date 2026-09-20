package com.example.hospital.domain;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public long version;
  public Instant createdAt = Instant.now();
  public Instant updatedAt = Instant.now();

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
