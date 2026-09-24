package com.example.hospital.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalTime;

public record TaskReminderPreferencesInput(
    @NotNull Boolean optedIn,
    @Pattern(regexp = "^[A-Za-z_+-]+(?:/[A-Za-z0-9_+.-]+)*$", message = "Choose a valid time zone.") String timeZone,
    @NotNull @Min(0) @Max(60) Integer minutesBefore,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd,
    @NotNull Boolean operationalAlerts) {}
