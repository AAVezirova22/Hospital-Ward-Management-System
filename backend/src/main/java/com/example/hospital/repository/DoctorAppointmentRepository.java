package com.example.hospital.repository;

import com.example.hospital.domain.DoctorAppointment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DoctorAppointmentRepository extends JpaRepository<DoctorAppointment, Long> {
  boolean existsByDoctorIdAndCancelledAtIsNullAndEndsAtAfter(Long doctorId, Instant now);
  @Query("""
      select a from DoctorAppointment a
      where a.doctorId = :doctorId and a.cancelledAt is null
        and a.startsAt < :endsAt and a.endsAt > :startsAt
      order by a.startsAt, a.id
      """)
  List<DoctorAppointment> overlapping(
      @Param("doctorId") Long doctorId, @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt);

  @Query("""
      select a from DoctorAppointment a
      where (:doctorId is null or a.doctorId = :doctorId)
        and a.startsAt >= :from and a.startsAt < :to
        and (:status = 'ALL' or (:status = 'SCHEDULED' and a.cancelledAt is null)
             or (:status = 'CANCELLED' and a.cancelledAt is not null))
        and lower(a.attendeeName) like :query escape '!'
      """)
  Page<DoctorAppointment> directory(
      @Param("doctorId") Long doctorId, @Param("from") Instant from,
      @Param("to") Instant to, @Param("status") String status,
      @Param("query") String query, Pageable pageable);
}
