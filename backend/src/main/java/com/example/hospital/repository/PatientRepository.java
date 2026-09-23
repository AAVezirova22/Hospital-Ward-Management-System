package com.example.hospital.repository;

import com.example.hospital.domain.Patient;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  @Query(
@Query(
    value = """
        select p from Patient p
        where p.departmentId = :departmentId
          and (
            :search = ''
            or lower(
              concat(
                concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')),
                concat(' ', coalesce(p.patientIdentifier, ''))
              )
            ) like concat('%', lower(:search), '%')
          )
          and (
            (:activeAdmission is null and :doctorId is null and :roomId is null)
            or (
              (:activeAdmission is null or :activeAdmission = true)
              and exists (
                select a from Admission a
                where a.patientId = p.id
                  and a.departmentId = :departmentId
                  and a.status = 'ACTIVE'
                  and (:doctorId is null or a.attendingDoctorId = :doctorId)
                  and (
                    :roomId is null
                    or exists (
                      select ra from RoomAssignment ra
                      where ra.admissionId = a.id
                        and ra.departmentId = :departmentId
                        and ra.releasedAt is null
                        and ra.roomId = :roomId
                    )
                  )
              )
            )
            or (
              :activeAdmission = false
              and :doctorId is null
              and :roomId is null
              and not exists (
                select a from Admission a
                where a.patientId = p.id
                  and a.departmentId = :departmentId
                  and a.status = 'ACTIVE'
              )
            )
          )
          and (
            :scopedDoctorId is null
            or exists (
              select a from Admission a
              where a.patientId = p.id
                and a.departmentId = :departmentId
                and a.attendingDoctorId = :scopedDoctorId
            )
          )
        order by lower(p.lastName), lower(p.firstName), p.id
        """,
    countQuery = """
        select count(p) from Patient p
        where p.departmentId = :departmentId
          and (
            :search = ''
            or lower(
              concat(
                concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, '')),
                concat(' ', coalesce(p.patientIdentifier, ''))
              )
            ) like concat('%', lower(:search), '%')
          )
          and (
            (:activeAdmission is null and :doctorId is null and :roomId is null)
            or (
              (:activeAdmission is null or :activeAdmission = true)
              and exists (
                select a from Admission a
                where a.patientId = p.id
                  and a.departmentId = :departmentId
                  and a.status = 'ACTIVE'
                  and (:doctorId is null or a.attendingDoctorId = :doctorId)
                  and (
                    :roomId is null
                    or exists (
                      select ra from RoomAssignment ra
                      where ra.admissionId = a.id
                        and ra.departmentId = :departmentId
                        and ra.releasedAt is null
                        and ra.roomId = :roomId
                    )
                  )
              )
            )
            or (
              :activeAdmission = false
              and :doctorId is null
              and :roomId is null
              and not exists (
                select a from Admission a
                where a.patientId = p.id
                  and a.departmentId = :departmentId
                  and a.status = 'ACTIVE'
              )
            )
          )
          and (
            :scopedDoctorId is null
            or exists (
              select a from Admission a
              where a.patientId = p.id
                and a.departmentId = :departmentId
                and a.attendingDoctorId = :scopedDoctorId
            )
          )
        """)
Page<Patient> findDirectory(
    @Param("search") String search,
    @Param("activeAdmission") Boolean activeAdmission,
    @Param("doctorId") Long doctorId,
    @Param("roomId") Long roomId,
    @Param("departmentId") Long departmentId,
    @Param("scopedDoctorId") Long scopedDoctorId,
    Pageable pageable);

Optional<Patient> findByDepartmentIdAndPatientIdentifier(
    Long departmentId,
    String patientIdentifier);
}
