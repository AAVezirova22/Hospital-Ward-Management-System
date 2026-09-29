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
   * The sortable register keys (#164).
   *
   * <p>A whitelist, not a convenience. A native query cannot bind a sort column as a
   * parameter, so the column text has to be chosen from a fixed set inside the
   * query; anything outside it is rejected rather than passed through.
   */
  String SORT_ADMISSION_DATE = "admissionDate";
  String SORT_PATIENT = "patient";
  String SORT_ROOM = "room";
  String SORT_STATUS = "status";


  /**
   * The register ordered by patient name (#164).
   *
   *   <p>Reaches the name through the join, which a JPQL {@code Pageable} cannot do, so
   *   the sort key selects one of four constant queries rather than splicing text into
   *   an ORDER BY. The direction is a multiplier on the sort expressions, so one
   *   query serves both directions; surname then forename, with the id as a final
   *   tiebreak so the order is total.
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
          order by
          (case when :descending then p.last_name end) desc nulls last,
          p.last_name asc,
          (case when :descending then p.first_name end) desc nulls last,
          p.first_name asc,
          (case when :descending then a.id end) desc nulls last,
          a.id asc
          """,
      nativeQuery = true)
  List<Admission> searchAdmissionsByPatient(
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
      @Param("descending") boolean descending,
      Pageable pageable);

  /**
   * The register ordered by the current room (#164).
   *
   *   <p>Room lives in {@code room_assignments} and {@code rooms}, so it needs its
   *   own query. The left joins keep an admission with no current room in the result,
   *   and the leading case puts those at the end in either direction rather than
   *   letting a null sort unpredictably.
   */
  @Query(value = """
          select a.* from admissions a
          join patients p on p.id = a.patient_id
          left join room_assignments ra
                 on ra.admission_id = a.id and ra.released_at is null
          left join rooms rm on rm.id = ra.room_id
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
          order by
          (case when rm.room_number is null then 1 else 0 end) asc,
          (case when :descending then rm.room_number end) desc nulls last,
          rm.room_number asc,
          a.admission_date_time desc, a.id desc
          """,
      nativeQuery = true)
  List<Admission> searchAdmissionsByRoom(
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
      @Param("descending") boolean descending,
      Pageable pageable);

  /**
   * The register ordered by status (#164), with the admission date and id as
   *   tiebreaks so rows sharing a status have a stable order across pages.
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
          order by
          (case when :descending then a.status end) desc nulls last,
          a.status asc,
          a.admission_date_time desc, a.id desc
          """,
      nativeQuery = true)
  List<Admission> searchAdmissionsByStatus(
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
      @Param("descending") boolean descending,
      Pageable pageable);


  /**
   * Free-text search over the admission number and the patient's name and
   * identifier (#163), with a caller-chosen sort (#164).
   *
   * <p>Native rather than JPQL: {@code Admission} holds a plain {@code patientId}
   * column rather than a relation, so a JPQL query cannot reach the patient's
   * name or identifier without adding a mapping this change does not need.
   *
   * <p>The term is matched case-insensitively on a prefix, with the pattern
   * escaped so a user typing a literal {@code %} does not turn into a wildcard
   * scan. Every LIKE is parameterised, so no part of the term is concatenated
   * into SQL.
   *
   * <p>The join to {@code room_assignments} is a left join, so an admission with no
   * current room still sorts and still appears. The sort itself is applied by
   * {@link com.example.hospital.service.AdmissionRegisterQuery}, which owns the
   * ORDER BY because a native query here cannot bind a sort column as a parameter.
   */
  @Query(value = """
          select a.* from admissions a
          join patients p on p.id = a.patient_id
          left join room_assignments ra
                 on ra.admission_id = a.id and ra.released_at is null
          left join rooms rm on rm.id = ra.room_id
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
