package com.example.hospital.repository;

import com.example.hospital.domain.AiSession;
import org.springframework.data.jpa.repository.*;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {
  java.util.Optional<AiSession> findBySessionKey(String sessionKey);
}
