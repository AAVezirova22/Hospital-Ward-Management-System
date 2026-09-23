package com.example.hospital.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Department notification policy (#320). {@code version} is 0 until the department saves its first policy. */
public record NotificationPolicyInput(
    @NotNull Boolean capacityAlertsEnabled,
    @NotNull Boolean activityNoticesEnabled,
    @NotNull @Min(1) @Max(99) Integer warningPercent,
    @NotNull @Min(2) @Max(100) Integer criticalPercent,
    @NotNull Boolean escalationEnabled,
    @NotNull @Pattern(regexp = "WARNING|CRITICAL") String escalationMinSeverity,
    @NotNull @Min(5) @Max(1440) Integer acknowledgementMinutes,
    @NotNull @Pattern(regexp = "ADMIN|MEDICAL_STAFF") String escalationRole,
    @NotNull @Min(1) @Max(90) Integer retentionDays,
    @Min(0) Long version) {}
