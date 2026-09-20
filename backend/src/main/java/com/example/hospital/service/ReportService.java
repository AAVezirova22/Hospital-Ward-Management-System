package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.Views;
import com.example.hospital.domain.Admission;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.PerformedProcedureRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Department dashboard, bed census, capacity and procedure reporting over saved records. */
@Service
@Transactional(readOnly = true)
public class ReportService {
  private final HospitalService hospital;
  private final StayService stays;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final PerformedProcedureRepository performed;
  private final Actor actor;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public ReportService(
      HospitalService hospital,
      StayService stays,
      AdmissionRepository admissions,
      RoomAssignmentRepository assignments,
      PerformedProcedureRepository performed,
      Actor actor,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.hospital = hospital;
    this.stays = stays;
    this.admissions = admissions;
    this.assignments = assignments;
    this.performed = performed;
    this.actor = actor;
    this.jdbc = jdbc;
  }

  public Map<String, Object> dashboard() {
    long departmentId = DepartmentContext.id();
    Long doctorId = actor.doctor() ? actor.user().getDoctorId() : null;
    Long activeAdmissions =
        doctorId == null
            ? jdbc.queryForObject(
                "select count(*) from admissions where department_id=? and status='ACTIVE'",
                Long.class,
                departmentId)
            : jdbc.queryForObject(
                "select count(*) from admissions where department_id=? and status='ACTIVE' and attending_doctor_id=?",
                Long.class,
                departmentId,
                doctorId);
    Long occupiedBeds =
        jdbc.queryForObject(
            "select count(*) from room_assignments ra join rooms r on r.id=ra.room_id where r.department_id=? and ra.released_at is null",
            Long.class,
            departmentId);
    Long totalBeds =
        jdbc.queryForObject(
            "select coalesce(sum(bed_count),0) from rooms where department_id=? and active=true",
            Long.class,
            departmentId);
    Long availableBeds =
        jdbc.queryForObject(
            """
            select coalesce(sum(r.bed_count),0) - (
              select count(*) from room_assignments ra join rooms x on x.id=ra.room_id
              where x.department_id=? and ra.released_at is null and x.active=true)
            from rooms r where r.department_id=? and r.active=true
            """,
            Long.class,
            departmentId,
            departmentId);
    Long activeDoctors =
        jdbc.queryForObject(
            "select count(*) from doctors where department_id=? and active=true",
            Long.class,
            departmentId);
    var start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Long proceduresToday =
        doctorId == null
            ? jdbc.queryForObject(
                "select count(*) from performed_procedures where department_id=? and performed_at>=? and performed_at<?",
                Long.class,
                departmentId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end))
            : jdbc.queryForObject(
                "select count(*) from performed_procedures pp join admissions a on a.id=pp.admission_id where pp.department_id=? and pp.performed_at>=? and pp.performed_at<? and a.attending_doctor_id=?",
                Long.class,
                departmentId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end),
                doctorId);
    return Map.of(
        "activeAdmissions",
        activeAdmissions == null ? 0 : activeAdmissions,
        "occupiedBeds",
        occupiedBeds == null ? 0 : occupiedBeds,
        "totalBeds",
        totalBeds == null ? 0 : totalBeds,
        "availableBeds",
        availableBeds == null ? 0 : availableBeds,
        "activeDoctors",
        activeDoctors == null ? 0 : activeDoctors,
        "proceduresToday",
        proceduresToday == null ? 0 : proceduresToday,
        "scope",
        actor.doctor() ? "Your assigned admissions; department bed capacity" : "Department");
  }

  public List<Map<String, Object>> census(Long roomId, Long doctorId) {
    return hospital.admissions().stream()
        .filter(
            a ->
                a.getStatus().equals("ACTIVE")
                    && (doctorId == null || a.getAttendingDoctorId().equals(doctorId)))
        .filter(
            a ->
                roomId == null
                    || assignments
                        .findByAdmissionIdAndReleasedAtIsNull(a.getId())
                        .map(ra -> ra.getRoomId().equals(roomId))
                        .orElse(false))
        .map(stays::view)
        .toList();
  }

  public List<Map<String, Object>> capacity() {
    return hospital.rooms(0);
  }

  public Map<String, Object> procedures(
      LocalDate from, LocalDate to, Long patientId, Long doctorId) {
    if (from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before end date.");
    if (patientId != null) hospital.accessible(patientId);
    var start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var allowed =
        hospital.admissions().stream()
            .filter(a -> patientId == null || a.getPatientId().equals(patientId))
            .map(a -> a.getId())
            .collect(Collectors.toSet());
    var ps =
        performed.findAll().stream()
            .filter(
                p ->
                    allowed.contains(p.getAdmissionId())
                        && !p.getPerformedAt().isBefore(start)
                        && p.getPerformedAt().isBefore(end)
                        && (doctorId == null || p.getPerformedByDoctorId().equals(doctorId)))
            .toList();
    var rows =
        ps.stream()
            .map(
                p -> {
                  Admission a = admissions.findById(p.getAdmissionId()).orElseThrow();
                  return Map.of(
                      "record",
                      Views.performed(p),
                      "patient",
                      Views.patient(hospital.patient(a.getPatientId())),
                      "doctor",
                      Views.doctor(hospital.doctor(p.getPerformedByDoctorId())),
                      "procedure",
                      Views.procedure(hospital.procedure(p.getMedicalProcedureId())));
                })
            .toList();
    Map<Long, BigDecimal> grouped = new LinkedHashMap<>();
    ps.forEach(p -> grouped.merge(p.getPerformedByDoctorId(), p.getPriceAtExecution(), BigDecimal::add));
    return Map.of(
        "rows",
        rows,
        "totalCost",
        ps.stream().map(p -> p.getPriceAtExecution()).reduce(BigDecimal.ZERO, BigDecimal::add),
        "byDoctor",
        grouped,
        "from",
        from,
        "to",
        to);
  }
}
