package com.example.hospital.repository;

import com.example.hospital.domain.WorkflowLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface WorkflowLockRepository extends JpaRepository<WorkflowLock, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WorkflowLock w where w.id=1")
  WorkflowLock acquire();
}
