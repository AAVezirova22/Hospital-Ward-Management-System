package com.example.hospital.repository;

import com.example.hospital.domain.PerformedProcedure;
import org.springframework.data.jpa.repository.*;

public interface PerformedProcedureRepository extends JpaRepository<PerformedProcedure, Long> {
  java.util.List<PerformedProcedure> findByAdmissionIdOrderByPerformedAtDesc(Long admissionId);
}
