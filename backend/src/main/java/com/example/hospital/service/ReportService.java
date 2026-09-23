package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
  private final HospitalService hospital;
  private final Actor actor;
  private final JdbcTemplate jdbc;
  private final DepartmentTimeService departmentTime;

  public ReportService(HospitalService hospital, Actor actor, JdbcTemplate jdbc,
      DepartmentTimeService departmentTime) {
    this.hospital = hospital;
    this.actor = actor;
    this.jdbc = jdbc;
    this.departmentTime = departmentTime;
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
    var zone = departmentTime.zoneId();
    var today = LocalDate.now(zone);
    var start = today.atStartOfDay(zone).toInstant();
    var end = today.plusDays(1).atStartOfDay(zone).toInstant();
    Long proceduresToday =
        doctorId == null
            ? jdbc.queryForObject(
                "select count(*) from performed_procedures where department_id=? and performed_at>=? and performed_at<?",
                Long.class,
                departmentId,
                Timestamp.from(start),
                Timestamp.from(end))
            : jdbc.queryForObject(
                "select count(*) from performed_procedures pp join admissions a on a.id=pp.admission_id where pp.department_id=? and pp.performed_at>=? and pp.performed_at<? and a.attending_doctor_id=?",
                Long.class,
                departmentId,
                Timestamp.from(start),
                Timestamp.from(end),
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
        actor.doctor() ? "Your assigned admissions; department bed capacity" : "Department",
        "timeZone",
        zone.getId());
  }

  public List<Map<String, Object>> census(Long roomId, Long doctorId) {
    long departmentId = DepartmentContext.id();
    var sql = new StringBuilder("select a.id from admissions a where a.department_id=? and a.status='ACTIVE'");
    var args = new ArrayList<Object>();
    args.add(departmentId);
    if (doctorId != null) {
      sql.append(" and a.attending_doctor_id=?");
      args.add(doctorId);
    }
    if (roomId != null) {
      sql.append(
          " and exists (select 1 from room_assignments ra where ra.admission_id=a.id and ra.released_at is null and ra.room_id=?)");
      args.add(roomId);
    }
    if (actor.doctor()) {
      sql.append(" and a.attending_doctor_id=?");
      args.add(actor.user().getDoctorId());
    }
    sql.append(" order by a.admission_date_time desc");
    return jdbc.query(
        sql.toString(),
        (rs, n) -> hospital.admissionView(hospital.admission(rs.getLong(1))),
        args.toArray());
  }

  public Map<String, Object> procedures(LocalDate from, LocalDate to, Long patientId, Long doctorId) {
    if (from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before end date.");
    if (patientId != null) hospital.accessible(patientId);
    long departmentId = DepartmentContext.id();
    var zone = departmentTime.zoneId();
    var start = Timestamp.from(from.atStartOfDay(zone).toInstant());
    var end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    var sql = new StringBuilder(
        """
        select pp.id rec_id, pp.admission_id, pp.medical_procedure_id, pp.performed_by_doctor_id,
               pp.performed_at, pp.price_at_execution, pp.note,
               p.id patient_id, p.version patient_version, p.patient_identifier, p.first_name, p.last_name,
               p.date_of_birth, p.address, p.phone_number,
               d.id doctor_id, d.version doctor_version, d.doctor_identifier, d.first_name doctor_first,
               d.last_name doctor_last, d.specialty, d.active doctor_active,
               mp.id proc_id, mp.version proc_version, mp.procedure_code, mp.procedure_name, mp.current_cost, mp.active proc_active
        from performed_procedures pp
        join admissions a on a.id=pp.admission_id
        join patients p on p.id=a.patient_id
        join doctors d on d.id=pp.performed_by_doctor_id
        join medical_procedures mp on mp.id=pp.medical_procedure_id
        where pp.department_id=? and pp.performed_at>=? and pp.performed_at<?
        """);
    var args = new ArrayList<Object>();
    args.add(departmentId);
    args.add(start);
    args.add(end);
    if (patientId != null) {
      sql.append(" and a.patient_id=?");
      args.add(patientId);
    }
    if (doctorId != null) {
      sql.append(" and pp.performed_by_doctor_id=?");
      args.add(doctorId);
    }
    if (actor.doctor()) {
      sql.append(" and a.attending_doctor_id=?");
      args.add(actor.user().getDoctorId());
    }
    sql.append(" order by pp.performed_at desc");
    List<Map<String, Object>> rows =
        jdbc.query(
            sql.toString(),
            (rs, n) -> {
              Map<String, Object> record = new LinkedHashMap<>();
              record.put("id", rs.getLong("rec_id"));
              record.put("admissionId", rs.getLong("admission_id"));
              record.put("medicalProcedureId", rs.getLong("medical_procedure_id"));
              record.put("performedByDoctorId", rs.getLong("performed_by_doctor_id"));
              record.put("performedAt", rs.getTimestamp("performed_at").toInstant());
              record.put("priceAtExecution", rs.getBigDecimal("price_at_execution"));
              record.put("note", rs.getString("note"));
              Map<String, Object> patient = new LinkedHashMap<>();
              patient.put("id", rs.getLong("patient_id"));
              patient.put("version", rs.getLong("patient_version"));
              patient.put("patientIdentifier", rs.getString("patient_identifier"));
              patient.put("firstName", rs.getString("first_name"));
              patient.put("lastName", rs.getString("last_name"));
              patient.put("dateOfBirth", rs.getDate("date_of_birth").toLocalDate());
              patient.put("address", rs.getString("address"));
              patient.put("phoneNumber", rs.getString("phone_number"));
              Map<String, Object> doctor = new LinkedHashMap<>();
              doctor.put("id", rs.getLong("doctor_id"));
              doctor.put("version", rs.getLong("doctor_version"));
              doctor.put("doctorIdentifier", rs.getString("doctor_identifier"));
              doctor.put("firstName", rs.getString("doctor_first"));
              doctor.put("lastName", rs.getString("doctor_last"));
              doctor.put("specialty", rs.getString("specialty"));
              doctor.put("active", rs.getBoolean("doctor_active"));
              Map<String, Object> procedure = new LinkedHashMap<>();
              procedure.put("id", rs.getLong("proc_id"));
              procedure.put("version", rs.getLong("proc_version"));
              procedure.put("procedureCode", rs.getString("procedure_code"));
              procedure.put("procedureName", rs.getString("procedure_name"));
              procedure.put("currentCost", rs.getBigDecimal("current_cost"));
              procedure.put("active", rs.getBoolean("proc_active"));
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("record", record);
              row.put("patient", patient);
              row.put("doctor", doctor);
              row.put("procedure", procedure);
              return row;
            },
            args.toArray());
    Map<Long, BigDecimal> grouped = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (var row : rows) {
      @SuppressWarnings("unchecked")
      var record = (Map<String, Object>) row.get("record");
      var price = (BigDecimal) record.get("priceAtExecution");
      var performedBy = (Long) record.get("performedByDoctorId");
      grouped.merge(performedBy, price, BigDecimal::add);
      total = total.add(price);
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("rows", rows);
    report.put("totalCost", total);
    report.put("byDoctor", grouped);
    report.put("from", from);
    report.put("to", to);
    report.put("timeZone", zone.getId());
    return report;
  }

  public List<Map<String, Object>> capacity() {
    return hospital.rooms(0);
  }
}
