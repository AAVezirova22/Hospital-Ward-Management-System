package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record RecordProcedureInput(
    @NotNull Long medicalProcedureId,
    @NotNull Long doctorId,
    @NotNull @PastOrPresent Instant performedAt,
    @Size(max = 2000) String note) {}
