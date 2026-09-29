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

  /**
   * Free-text search over the admission number and the patient's name and
   * identifier (#163).
   *
   * <p>Native rather than JPQL: {@code Admission} holds a plain {@code patientId}
   * column rather than a relation, so a JPQL query cannot reach the patient's
   * name or identifier without adding a mapping this change does not need.
   *
   * <p>The term is matched case-insensitively on a prefix, with the pattern
   * escaped so a user typing a literal {@code %} does not turn into a wildcard
   * scan. Every LIKE is parameterised, so no part of the term is concatenated
   * into SQL.
   */
  @Query(value = """
          select a.* from admissions a
          join patients p on p.id = a.patient_id
          where a.department_id = :departmentId
            and (:hasSearch = false
                 or a.admission_number ilike :searchPrefix escape '\\'
                 or p.patient_identifier ilike :searchPrefix escape '\\'
                 or p.first_name ilike :searchPrefix escape '\\'
                 or p.last_name ilike :searchPrefix escape '\\'
                 or (p.first_name || ' ' || p.last_name) ilike :searchPrefix escape '\\')
            and (:hasStatus = false or a.status = :status)
            and (:hasFromDate = false or a.admission_date_time >= :fromDate)
            and (:hasToDate = false or a.admission_date_time < :toDateExclusive)
            and (:hasDoctorId = false or a.attending_doctor_id = :doctorId)
            and (:doctorScoped = false or a.attending_doctor_id = :scopedDoctorId)
            and (:patientScoped = false or a.patient_id = :scopedPatientId)
          order by a.admission_date_time desc, a.id desc
          """,
      nativeQuery = true)
  List<Admission> searchAdmissionsText(
      @Param("departmentId") Long departmentId,
      @Param("hasSearch") boolean hasSearch,
      @Param("searchPrefix") String searchPrefix,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasFromDate") boolean hasFromDate,
      @Param("fromDate") Instant fromDate,
      @Param("hasToDate") boolean hasToDate,
      @Param("toDateExclusive") Instant toDateExclusive,
      @Param("hasDoctorId") boolean hasDoctorId,
      @Param("doctorId") Long doctorId,
      @Param("doctorScoped") boolean doctorScoped,
      @Param("scopedDoctorId") Long scopedDoctorId,
      @Param("patientScoped") boolean patientScoped,
      @Param("scopedPatientId") Long scopedPatientId,
      Pageable pageable);

  @Query(value = """
          select count(*) from admissions a
          join patients p on p.id = a.patient_id
          where a.department_id = :departmentId
            and (:hasSearch = false
                 or a.admission_number ilike :searchPrefix escape '\\'
                 or p.patient_identifier ilike :searchPrefix escape '\\'
                 or p.first_name ilike :searchPrefix escape '\\'
                 or p.last_name ilike :searchPrefix escape '\\'
                 or (p.first_name || ' ' || p.last_name) ilike :searchPrefix escape '\\')
            and (:hasStatus = false or a.status = :status)
            and (:hasFromDate = false or a.admission_date_time >= :fromDate)
            and (:hasToDate = false or a.admission_date_time < :toDateExclusive)
            and (:hasDoctorId = false or a.attending_doctor_id = :doctorId)
            and (:doctorScoped = false or a.attending_doctor_id = :scopedDoctorId)
            and (:patientScoped = false or a.patient_id = :scopedPatientId)
          """,
      nativeQuery = true)
  long countSearchAdmissionsText(
      @Param("departmentId") Long departmentId,
      @Param("hasSearch") boolean hasSearch,
      @Param("searchPrefix") String searchPrefix,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasFromDate") boolean hasFromDate,
      @Param("fromDate") Instant fromDate,
      @Param("hasToDate") boolean hasToDate,
      @Param("toDateExclusive") Instant toDateExclusive,
      @Param("hasDoctorId") boolean hasDoctorId,
      @Param("doctorId") Long doctorId,
      @Param("doctorScoped") boolean doctorScoped,
      @Param("scopedDoctorId") Long scopedDoctorId,
      @Param("patientScoped") boolean patientScoped,
      @Param("scopedPatientId") Long scopedPatientId);

  @Query("""
          select a from Admission a
          where a.departmentId = :departmentId
            and (:hasStatus = false or a.status = :status)
            and (:hasFromDate = false or a.admissionDateTime >= :fromDate)
            and (:hasToDate = false or a.admissionDateTime < :toDateExclusive)
            and (:hasDoctorId = false or a.attendingDoctorId = :doctorId)
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
