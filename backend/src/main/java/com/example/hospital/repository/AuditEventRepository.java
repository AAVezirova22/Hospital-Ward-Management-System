package com.example.hospital.repository;

import com.example.hospital.domain.AuditEvent;
import org.springframework.data.jpa.repository.*;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {}
