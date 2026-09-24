package com.example.hospital.repository;

import com.example.hospital.domain.Room;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface RoomRepository extends JpaRepository<Room, Long> {
  @Query("""
      select r from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasRoomId = false or r.id = :roomId)
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
      """)
  List<Room> searchDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
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
      """)
  List<Room> searchDirectoryWithCapabilities(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
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
      """)
  long countDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree);

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
      """)
  long countDirectoryWithCapabilities(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasRoomId") boolean hasRoomId,
      @Param("roomId") Long roomId,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      @Param("requiredCapabilities") List<String> requiredCapabilities,
      @Param("requiredCapabilityCount") int requiredCapabilityCount);
}
