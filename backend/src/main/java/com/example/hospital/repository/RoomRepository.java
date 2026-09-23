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
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
      """)
  List<Room> searchDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree,
      Pageable pageable);

  @Query("""
      select count(r) from Room r
      where (:hasQuery = false or lower(r.roomNumber) like :query escape '!')
        and (:hasActive = false or r.active = :active)
        and (case when r.active = true then r.bedCount -
          (select count(a.id) from RoomAssignment a
            where a.roomId = r.id and a.releasedAt is null) else 0 end) >= :minFree
      """)
  long countDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("minFree") int minFree);
}
