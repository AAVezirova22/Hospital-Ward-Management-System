package com.example.hospital.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionInput(
    @NotBlank @Size(max = 2048) String endpoint,
    @NotBlank @Size(max = 256) String p256dh,
    @NotBlank @Size(max = 256) String auth) {}
