package com.example.hospital.repository;

import com.example.hospital.domain.MedicalProcedure;
import org.springframework.data.jpa.repository.*;

public interface MedicalProcedureRepository extends JpaRepository<MedicalProcedure, Long> {}
