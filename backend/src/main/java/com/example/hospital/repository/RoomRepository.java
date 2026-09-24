package com.example.hospital.repository;

import com.example.hospital.domain.Room;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface RoomRepository extends JpaRepository<Room, Long> {
  // The peak reservation occurs at a hold start. Only unexpired, uncancelled
  // holds participate, including holds that already started. This keeps the
  // availability filter consistent with BedHoldCapacity.reserved before paging.
  String HOLD_CAPACITY_FILTER = """
        and (:minFree = 0 or not exists (
          select point.id from BedHold point
          where point.roomId = r.id and point.cancelledAt is null and point.endsAt > :now
            and (select coalesce(sum(h.bedCount), 0) from BedHold h
              where h.roomId = r.id and h.cancelledAt is null and h.endsAt > :now
                and h.startsAt <= point.startsAt and h.endsAt > point.startsAt)
              > r.bedCount - (select count(a.id) from RoomAssignment a
                where a.roomId = r.id and a.releasedAt is null) - :minFree
        ))
      """;

  @Query("""
      select r from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasRoomId = false or r.id = :roomId)
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
      """ + HOLD_CAPACITY_FILTER)
  List<Room> searchDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      @Param("now") Instant now,
      Pageable pageable);

  @Query("""
      select r from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasRoomId = false or r.id = :roomId)
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
        and (select count(distinct capability) from Room candidate
          join candidate.capabilities capability
          where candidate.id = r.id and capability in :requiredCapabilities) = :requiredCapabilityCount
      """ + HOLD_CAPACITY_FILTER)
  List<Room> searchDirectoryWithCapabilities(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      @Param("now") Instant now,
      @Param("requiredCapabilities") List<String> requiredCapabilities,
      @Param("requiredCapabilityCount") int requiredCapabilityCount,
      Pageable pageable);

  @Query("""
      select count(r) from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasRoomId = false or r.id = :roomId)
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
      """ + HOLD_CAPACITY_FILTER)
  long countDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      @Param("now") Instant now);

  @Query("""
      select count(r) from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasRoomId = false or r.id = :roomId)
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
        and (select count(distinct capability) from Room candidate
          join candidate.capabilities capability
          where candidate.id = r.id and capability in :requiredCapabilities) = :requiredCapabilityCount
      """ + HOLD_CAPACITY_FILTER)
  long countDirectoryWithCapabilities(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      @Param("now") Instant now,
      @Param("requiredCapabilities") List<String> requiredCapabilities,
      @Param("requiredCapabilityCount") int requiredCapabilityCount);
}
