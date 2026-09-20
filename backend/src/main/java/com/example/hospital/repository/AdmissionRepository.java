package com.example.hospital.repository;

import com.example.hospital.domain.Admission;
import org.springframework.data.jpa.repository.*;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {
  java.util.List<Admission> findByPatientIdOrderByAdmissionDateTimeDesc(Long patientId);

  java.util.Optional<Admission> findByPatientIdAndStatus(Long patientId, String status);

  boolean existsByPatientIdAndAttendingDoctorId(Long patientId, Long doctorId);

  boolean existsByAttendingDoctorIdAndStatus(Long doctorId, String status);
}
