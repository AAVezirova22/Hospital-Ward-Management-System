package com.example.hospital.repository;

import com.example.hospital.domain.RoomAssignment;
import org.springframework.data.jpa.repository.*;

public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, Long> {
  java.util.Optional<RoomAssignment> findByAdmissionIdAndReleasedAtIsNull(Long admissionId);

  long countByRoomIdAndReleasedAtIsNull(Long roomId);

  java.util.List<RoomAssignment> findByAdmissionIdOrderByAssignedAt(Long admissionId);

  @Query("""
      select a.roomId, count(a.id) from RoomAssignment a
      where a.roomId in :roomIds and a.releasedAt is null
      group by a.roomId
      """)
  java.util.List<Object[]> countActiveByRoomIds(@org.springframework.data.repository.query.Param("roomIds") java.util.Collection<Long> roomIds);
}
