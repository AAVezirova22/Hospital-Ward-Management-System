package com.example.hospital.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PatientInput(
    @NotBlank @Size(max = 64) String patientIdentifier,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotNull @PastOrPresent LocalDate dateOfBirth,
    @Size(max = 500) String address,
    @Size(max = 40) @Pattern(regexp = "|\\+[1-9]\\d{7,14}", message = "Use E.164, for example +359888123456") String phoneNumber,
    Long version) {}
