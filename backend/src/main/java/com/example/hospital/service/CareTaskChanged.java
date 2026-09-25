package com.example.hospital.service;

import java.time.Instant;

/** Published after a care task write so reminder delivery can reconcile its recipient and due time. */
public record CareTaskChanged(
    long departmentId, long taskId, Long assignedUserId, Instant dueAt, String status) {}
