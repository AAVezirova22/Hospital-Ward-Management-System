package com.example.hospital.repository;

import com.example.hospital.domain.Doctor;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
  @Query("""
      select d from Doctor d
      where (:hasQuery = false or lower(d.doctorIdentifier) like :query escape '!'
          or lower(d.firstName) like :query escape '!' or lower(d.lastName) like :query escape '!'
          or lower(d.specialty) like :query escape '!')
        and (:hasActive = false or d.active = :active)
        and (:specialty = '' or lower(d.specialty) = lower(:specialty))
      """)
  List<Doctor> searchDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("specialty") String specialty,
      Pageable pageable);

  @Query("""
      select count(d) from Doctor d
      where (:hasQuery = false or lower(d.doctorIdentifier) like :query escape '!'
          or lower(d.firstName) like :query escape '!' or lower(d.lastName) like :query escape '!'
          or lower(d.specialty) like :query escape '!')
        and (:hasActive = false or d.active = :active)
        and (:specialty = '' or lower(d.specialty) = lower(:specialty))
      """)
  long countDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      @Param("specialty") String specialty);

  @Query("select distinct d.specialty from Doctor d where d.specialty is not null and d.specialty <> '' order by d.specialty")
  List<String> findDistinctSpecialties();
}
