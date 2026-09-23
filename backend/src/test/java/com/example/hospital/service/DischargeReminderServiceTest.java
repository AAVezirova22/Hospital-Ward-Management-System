package com.example.hospital.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DischargeReminderServiceTest {
  @Test
  void parsesAndSortsConfiguredWindows() {
    assertThat(DischargeReminderService.parseWindows("3, 1,7")).containsExactly(1, 3, 7);
  }

  @Test
  void rejectsEmptyDuplicateAndOutOfRangeWindows() {
    for (var configured : List.of("", "1,1", "0", "366", "tomorrow")) {
      assertThatThrownBy(() -> DischargeReminderService.parseWindows(configured))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void reminderMailDoesNotRequireAConfirmationLinkOrigin() {
    var email = new ConfirmationEmailService(
        "provider-key", "Medcore <alerts@example.test>", "", "https://api.example.test/emails",
        new ObjectMapper());

    assertThat(email.configured()).isFalse();
    assertThat(email.remindersConfigured()).isTrue();
  }
}
