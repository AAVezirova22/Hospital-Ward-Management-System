package com.example.hospital.repository;

import com.example.hospital.domain.AiInteraction;
import org.springframework.data.jpa.repository.*;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {
  java.util.List<AiInteraction> findTop50BySessionIdAndUserIdOrderByStartedAtDesc(
      String sessionId, Long userId);
}
