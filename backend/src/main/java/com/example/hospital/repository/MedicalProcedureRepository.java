package com.example.hospital.repository;

import com.example.hospital.domain.MedicalProcedure;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface MedicalProcedureRepository extends JpaRepository<MedicalProcedure, Long> {
  @Query("""
      select p from MedicalProcedure p
      where (:hasQuery = false or lower(p.procedureCode) like :query escape '!'
          or lower(p.procedureName) like :query escape '!')
        and (:hasActive = false or p.active = :active)
      """)
  List<MedicalProcedure> searchDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active,
      Pageable pageable);

  @Query("""
      select count(p) from MedicalProcedure p
      where (:hasQuery = false or lower(p.procedureCode) like :query escape '!'
          or lower(p.procedureName) like :query escape '!')
        and (:hasActive = false or p.active = :active)
      """)
  long countDirectory(
      @Param("hasQuery") boolean hasQuery,
      @Param("query") String query,
      @Param("hasActive") boolean hasActive,
      @Param("active") boolean active);
}
