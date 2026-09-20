package com.example.hospital.repository;

import com.example.hospital.domain.Doctor;
import org.springframework.data.jpa.repository.*;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {}
