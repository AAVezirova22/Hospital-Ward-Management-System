package com.example.hospital.repository;

import com.example.hospital.domain.Patient;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  @Query(
      value = """
          select p from Patient p
          where p.departmentId = :departmentId
            and (:doctorId is null or exists (
              select a.id from Admission a
              where a.departmentId = :departmentId
                and a.patientId = p.id
                and a.attendingDoctorId = :doctorId
            ))
            and (:query = '' or locate(:query, lower(concat(concat(concat(p.firstName, ' '), concat(p.lastName, ' ')), p.patientIdentifier))) > 0)
          """,
      countQuery = """
          select count(p) from Patient p
          where p.departmentId = :departmentId
            and (:doctorId is null or exists (
              select a.id from Admission a
              where a.departmentId = :departmentId
                and a.patientId = p.id
                and a.attendingDoctorId = :doctorId
            ))
            and (:query = '' or locate(:query, lower(concat(concat(concat(p.firstName, ' '), concat(p.lastName, ' ')), p.patientIdentifier))) > 0)
          """)
  Page<Patient> searchDirectory(
      @Param("departmentId") Long departmentId,
      @Param("doctorId") Long doctorId,
      @Param("query") String query,
      Pageable pageable);

  Optional<Patient> findByDepartmentIdAndPatientIdentifier(Long departmentId, String patientIdentifier);
}
