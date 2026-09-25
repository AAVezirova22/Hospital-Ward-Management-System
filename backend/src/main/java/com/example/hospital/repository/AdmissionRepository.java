package com.example.hospital.repository;

import com.example.hospital.domain.Admission;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {
  java.util.List<Admission> findByPatientIdOrderByAdmissionDateTimeDesc(Long patientId);

  java.util.Optional<Admission> findByPatientIdAndStatus(Long patientId, String status);

  boolean existsByPatientIdAndAttendingDoctorId(Long patientId, Long doctorId);

  boolean existsByAttendingDoctorIdAndStatus(Long doctorId, String status);

  @Query("""
          select a from Admission a
          where a.departmentId = :departmentId
            and (:hasStatus = false or a.status = :status)
            and (:hasFromDate = false or a.admissionDateTime >= :fromDate)
            and (:hasToDate = false or a.admissionDateTime < :toDateExclusive)
            and (:hasDoctorId = false or a.attendingDoctorId = :doctorId)
            and (:hasQuery = false or locate(:query, lower(a.admissionNumber)) > 0 or exists (
              select p.id from Patient p where p.id = a.patientId and p.departmentId = a.departmentId
                and (locate(:query, lower(p.patientIdentifier)) > 0 or locate(:query, lower(p.firstName)) > 0 or locate(:query, lower(p.lastName)) > 0
                  or locate(:query, lower(concat(concat(p.firstName, ' '), p.lastName))) > 0)
            ))
            and (:doctorScoped = false or a.attendingDoctorId = :scopedDoctorId)
            and (:patientScoped = false or a.patientId = :scopedPatientId)
          """)
  List<Admission> searchAdmissions(
      @Param("departmentId") Long departmentId,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasFromDate") boolean hasFromDate,
      @Param("fromDate") Instant fromDate,
      @Param("hasToDate") boolean hasToDate,
      @Param("toDateExclusive") Instant toDateExclusive,
      @Param("hasDoctorId") boolean hasDoctorId,
      @Param("doctorId") Long doctorId,
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("doctorScoped") boolean doctorScoped,
      @Param("scopedDoctorId") Long scopedDoctorId,
      @Param("patientScoped") boolean patientScoped,
      @Param("scopedPatientId") Long scopedPatientId,
      Pageable pageable);

  @Query("""
          select count(a) from Admission a
          where a.departmentId = :departmentId
            and (:hasStatus = false or a.status = :status)
            and (:hasFromDate = false or a.admissionDateTime >= :fromDate)
            and (:hasToDate = false or a.admissionDateTime < :toDateExclusive)
            and (:hasDoctorId = false or a.attendingDoctorId = :doctorId)
            and (:hasQuery = false or locate(:query, lower(a.admissionNumber)) > 0 or exists (
              select p.id from Patient p where p.id = a.patientId and p.departmentId = a.departmentId
                and (locate(:query, lower(p.patientIdentifier)) > 0 or locate(:query, lower(p.firstName)) > 0 or locate(:query, lower(p.lastName)) > 0
                  or locate(:query, lower(concat(concat(p.firstName, ' '), p.lastName))) > 0)
            ))
            and (:doctorScoped = false or a.attendingDoctorId = :scopedDoctorId)
            and (:patientScoped = false or a.patientId = :scopedPatientId)
          """)
  long countSearchAdmissions(
      @Param("departmentId") Long departmentId,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasFromDate") boolean hasFromDate,
      @Param("fromDate") Instant fromDate,
      @Param("hasToDate") boolean hasToDate,
      @Param("toDateExclusive") Instant toDateExclusive,
      @Param("hasDoctorId") boolean hasDoctorId,
      @Param("doctorId") Long doctorId,
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("doctorScoped") boolean doctorScoped,
      @Param("scopedDoctorId") Long scopedDoctorId,
      @Param("patientScoped") boolean patientScoped,
      @Param("scopedPatientId") Long scopedPatientId);

  @Query("""
      select a from Admission a
      where a.departmentId = :departmentId
        and (:doctorScoped = false or a.attendingDoctorId = :scopedDoctorId)
        and (:patientScoped = false or a.patientId = :scopedPatientId)
      """)
  List<Admission> findVisibleAdmissions(
      @Param("departmentId") Long departmentId,
      @Param("doctorScoped") boolean doctorScoped,
      @Param("scopedDoctorId") Long scopedDoctorId,
      @Param("patientScoped") boolean patientScoped,
      @Param("scopedPatientId") Long scopedPatientId,
      Sort sort);
}
