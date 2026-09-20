package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProcedureInput(
    @NotBlank @Size(max = 64) String procedureCode,
    @NotBlank @Size(max = 150) String procedureName,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal currentCost,
    boolean active,
    Long version) {}
