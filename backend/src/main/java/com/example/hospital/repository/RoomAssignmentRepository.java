package com.example.hospital.repository;

import com.example.hospital.domain.RoomAssignment;
import org.springframework.data.jpa.repository.*;

public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, Long> {
  java.util.Optional<RoomAssignment> findByAdmissionIdAndReleasedAtIsNull(Long admissionId);

  long countByRoomIdAndReleasedAtIsNull(Long roomId);

  java.util.List<RoomAssignment> findByRoomIdAndReleasedAtIsNull(Long roomId);

  java.util.List<RoomAssignment> findByAdmissionIdOrderByAssignedAt(Long admissionId);
}
