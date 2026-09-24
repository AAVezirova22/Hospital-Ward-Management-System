package com.example.hospital.repository;

import com.example.hospital.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
  Page<AuditEvent> findByEventTypeIgnoreCase(String eventType, Pageable pageable);

  boolean existsByUserIdAndEventTypeAndEntityIdAndTimestampAfter(
      Long userId, String eventType, Long entityId, java.time.Instant after);
}
