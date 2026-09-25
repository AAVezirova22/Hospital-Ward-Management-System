package com.example.hospital.repository;

import com.example.hospital.domain.RoomBed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBedRepository extends JpaRepository<RoomBed, Long> {
  List<RoomBed> findByRoomIdOrderByIdentifier(Long roomId);
  Optional<RoomBed> findByRoomIdAndIdentifier(Long roomId, String identifier);
  long countByRoomId(Long roomId);
  long countByRoomIdAndIdentifierNotIn(Long roomId, List<String> identifiers);
}
