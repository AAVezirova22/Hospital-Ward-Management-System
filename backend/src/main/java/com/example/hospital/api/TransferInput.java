package com.example.hospital.api;

import jakarta.validation.constraints.*;

public record TransferInput(
    @NotNull @Positive Long roomId,
    @NotBlank @Size(max = 500) String reason,
    @NotNull Long version,
    @Size(max = 64) String bedIdentifier) {}
