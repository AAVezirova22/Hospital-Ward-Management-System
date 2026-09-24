package com.example.hospital.repository;

import com.example.hospital.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository
    extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {}
