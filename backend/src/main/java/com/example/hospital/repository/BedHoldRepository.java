package com.example.hospital.repository;

import com.example.hospital.domain.BedHold;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BedHoldRepository extends JpaRepository<BedHold, Long> {
  List<BedHold> findByRoomIdAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(
      Long roomId, Instant after);

  List<BedHold> findByRoomIdInAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(
      List<Long> roomIds, Instant after);

  List<BedHold> findByCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(Instant after);
}
