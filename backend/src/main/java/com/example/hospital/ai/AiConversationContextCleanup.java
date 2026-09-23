package com.example.hospital.ai;

import com.example.hospital.repository.AiSessionRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiConversationContextCleanup {
  private final AiSessionRepository sessions;

  public AiConversationContextCleanup(AiSessionRepository sessions) {
    this.sessions = sessions;
  }

  @Scheduled(fixedDelayString = "${app.ai.context-cleanup-interval:1m}")
  public void clearExpiredContext() {
    sessions.clearExpiredConversationContext(Instant.now());
  }
}
