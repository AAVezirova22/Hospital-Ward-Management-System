package com.example.hospital.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailSenderValidationTest {
  @Test
  void acceptsBareAndNamedAddresses() {
    assertThat(ConfirmationEmailService.validSender("wards@example.org")).isTrue();
    assertThat(ConfirmationEmailService.validSender("Medcore <onboarding@resend.dev>")).isTrue();
  }

  @Test
  void rejectsMalformedSenders() {
    assertThat(ConfirmationEmailService.validSender("")).isFalse();
    assertThat(ConfirmationEmailService.validSender("Medcore")).isFalse();
    assertThat(ConfirmationEmailService.validSender("Medcore <wards@example>")).isFalse();
    assertThat(ConfirmationEmailService.validSender("wards@@example.org")).isFalse();
    assertThat(ConfirmationEmailService.validSender("<wards@example.org>")).isFalse();
  }
}
