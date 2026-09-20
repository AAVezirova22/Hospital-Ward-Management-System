package com.example.hospital.repository;

import com.example.hospital.domain.AiPendingAction;
import org.springframework.data.jpa.repository.*;

public interface AiPendingActionRepository extends JpaRepository<AiPendingAction, Long> {}
