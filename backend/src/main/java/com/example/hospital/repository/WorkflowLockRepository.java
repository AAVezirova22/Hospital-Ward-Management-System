package com.example.hospital.repository;

import com.example.hospital.domain.WorkflowLock;
import com.example.hospital.security.DepartmentContext;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkflowLockRepository extends JpaRepository<WorkflowLock, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WorkflowLock w where w.id=:id")
  WorkflowLock lockById(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WorkflowLock w order by w.id")
  List<WorkflowLock> lockAll();

  /** Locks the current department, or the global sentinel when no department is in scope. */
  default WorkflowLock acquire() {
    long id = DepartmentContext.id();
    return lockById(id > 0 ? id : 0L);
  }

  default List<WorkflowLock> acquireAll() {
    return lockAll();
  }
}
