package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EmailOutboxDispatcher {
  private final EmailOutboxStore outbox;
  private final ConfirmationEmailService email;
  private final int batchSize;

  public EmailOutboxDispatcher(EmailOutboxStore outbox, ConfirmationEmailService email,
      @Value("${app.email.outbox.batch-size:10}") int batchSize) {
    this.outbox = outbox;
    this.email = email;
    this.batchSize = Math.max(1, Math.min(batchSize, 10));
  }

  @Scheduled(fixedDelayString = "${app.email.outbox.poll-interval-ms:5000}",
      initialDelayString = "${app.email.outbox.initial-delay-ms:5000}")
  public void dispatchDue() {
    outbox.cleanup();
    for (var delivery : outbox.claimDue(batchSize)) {
      try {
        email.send(delivery.recipient(), delivery.firstName(), delivery.token(),
            delivery.doctor(), delivery.expiryMinutes());
      } catch (RuntimeException failure) {
        outbox.retryOrFinish(delivery, safeError(failure));
        continue;
      }
      // If this update fails after provider acceptance, the lease permits a retry with
      // the same token; ConfirmationEmailService sends a stable provider idempotency key.
      outbox.markSent(delivery.id());
    }
  }

  private static String safeError(RuntimeException failure) {
    String code = failure instanceof ApiException api ? api.getCode() : null;
    return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "EMAIL_DELIVERY_FAILED";
  }
}
