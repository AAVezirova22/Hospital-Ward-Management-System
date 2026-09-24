package com.example.hospital.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.security.Actor;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AuditServiceReadTest {
  private final AuditEventRepository events = mock(AuditEventRepository.class);
  private final Actor actor = mock(Actor.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

  @BeforeEach
  void signedIn() {
    var user = new AppUser();
    user.setId(7L);
    when(actor.user()).thenReturn(user);
  }

  @Test
  void repeatViewsInsideTheWindowAreSkippedAndReadsPublishNothing() {
    var audit = new AuditService(events, actor, publisher, true, Duration.ofMinutes(15));
    when(events.existsByUserIdAndEventTypeAndEntityIdAndTimestampAfter(
            eq(7L), eq("PATIENT_VIEWED"), eq(3L), any(Instant.class)))
        .thenReturn(false, true);

    audit.read("PATIENT_VIEWED", "Patient", 3L, "UI");
    audit.read("PATIENT_VIEWED", "Patient", 3L, "UI");

    verify(events, times(1)).save(any());
    verifyNoInteractions(publisher);
  }

  @Test
  void aZeroWindowRecordsEveryView() {
    var audit = new AuditService(events, actor, publisher, true, Duration.ZERO);

    audit.read("ADMISSION_VIEWED", "Admission", 9L, "UI");
    audit.read("ADMISSION_VIEWED", "Admission", 9L, "UI");

    verify(events, times(2)).save(any());
    verify(events, never())
        .existsByUserIdAndEventTypeAndEntityIdAndTimestampAfter(anyLong(), any(), anyLong(), any());
  }

  @Test
  void readAuditingCanBeTurnedOff() {
    var audit = new AuditService(events, actor, publisher, false, Duration.ofMinutes(15));

    audit.read("PATIENT_VIEWED", "Patient", 3L, "UI");

    verifyNoInteractions(events);
  }

  @Test
  void negativeWindowsAreRejected() {
    assertThatThrownBy(() -> new AuditService(events, actor, publisher, true, Duration.ofMinutes(-1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUDIT_READ_DEDUPE_WINDOW");
  }
}
