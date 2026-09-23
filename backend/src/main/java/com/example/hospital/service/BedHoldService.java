package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.BedHoldInput;
import com.example.hospital.api.Views;
import com.example.hospital.domain.BedHold;
import com.example.hospital.repository.BedHoldRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.example.hospital.repository.RoomRepository;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BedHoldService {
  private final BedHoldRepository holds;
  private final RoomRepository rooms;
  private final RoomAssignmentRepository assignments;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AuditService audit;

  public BedHoldService(
      BedHoldRepository holds,
      RoomRepository rooms,
      RoomAssignmentRepository assignments,
      WorkflowLockRepository lock,
      Actor actor,
      AuditService audit) {
    this.holds = holds;
    this.rooms = rooms;
    this.assignments = assignments;
    this.lock = lock;
    this.actor = actor;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> create(Long roomId, BedHoldInput input) {
    lock.acquire();
    actor.staff();
    var room = rooms.findById(roomId).orElseThrow(ApiException::missing);
    if (!room.isActive())
      throw ApiException.conflict("ROOM_INACTIVE", "Maintenance holds can only be scheduled for active rooms.");
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    var startsAt = input.startsAt().truncatedTo(ChronoUnit.MICROS);
    var endsAt = input.endsAt().truncatedTo(ChronoUnit.MICROS);
    if (!endsAt.isAfter(startsAt) || !endsAt.isAfter(now))
      throw new ApiException(400, "INVALID_HOLD_WINDOW", "Choose an end time after the start and in the future.");

    var existing = holds.findByRoomIdAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(roomId, now);
    int reserved = BedHoldCapacity.peakDuring(existing, startsAt, endsAt);
    long occupied = assignments.countByRoomIdAndReleasedAtIsNull(roomId);
    if (occupied + reserved + input.bedCount() > room.getBedCount())
      throw ApiException.conflict(
          "ROOM_CAPACITY_EXCEEDED", "The room does not have enough unoccupied capacity for this hold window.");

    var hold = new BedHold();
    hold.setRoomId(roomId);
    hold.setBedCount(input.bedCount());
    hold.setReason(input.reason().trim());
    hold.setStartsAt(startsAt);
    hold.setEndsAt(endsAt);
    hold.setCreatedBy(actor.user().getId());
    holds.saveAndFlush(hold);
    audit.log("BED_HOLD_CREATED", "BedHold", hold.getId(), "UI");
    return Views.bedHold(hold);
  }

  @Transactional
  public void cancel(Long roomId, Long holdId) {
    lock.acquire();
    actor.staff();
    var hold = holds.findById(holdId).orElseThrow(ApiException::missing);
    if (!hold.getRoomId().equals(roomId)) throw ApiException.missing();
    if (hold.getCancelledAt() != null) return;
    hold.setCancelledAt(Instant.now());
    hold.setCancelledBy(actor.user().getId());
    holds.saveAndFlush(hold);
    audit.log("BED_HOLD_CANCELLED", "BedHold", hold.getId(), "UI");
  }

}
