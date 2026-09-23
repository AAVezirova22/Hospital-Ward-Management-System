package com.example.hospital.repository;

import com.example.hospital.domain.AiSession;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiSessionRepository extends JpaRepository<AiSession, Long> {
  java.util.Optional<AiSession> findBySessionKey(String sessionKey);

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      value = "update ai_sessions set conversation_context = null, conversation_expires_at = null, "
          + "version = version + 1, updated_at = current_timestamp "
          + "where conversation_context is not null and conversation_expires_at <= :now",
      nativeQuery = true)
  int clearExpiredConversationContext(@Param("now") Instant now);
}
