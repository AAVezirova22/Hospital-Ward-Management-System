package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
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
    var rooms = hospital.rooms(0).stream().filter(r -> Boolean.TRUE.equals(r.get("active"))).toList();
    long totalBeds = rooms.stream().mapToLong(r -> ((Number) r.get("bedCount")).longValue()).sum();
    long heldBeds = rooms.stream().mapToLong(r -> ((Number) r.get("heldBeds")).longValue()).sum();
    long availableBeds = rooms.stream().mapToLong(r -> ((Number) r.get("availableBeds")).longValue()).sum();
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
        totalBeds,
        "heldBeds",
        heldBeds,
        "availableBeds",
        availableBeds,
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
    return procedures(from, to, patientId, doctorId, null);
  }

  public Map<String, Object> procedures(
      LocalDate from, LocalDate to, Long patientId, Long doctorId, Long medicalProcedureId) {
    if (from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before end date.");
    if (patientId != null) hospital.accessible(patientId);
    long departmentId = DepartmentContext.id();
    var zone = departmentTime.zoneId();
    var start = Timestamp.from(from.atStartOfDay(zone).toInstant());
    var end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    var sql = new StringBuilder(
        """
        select pp.id rec_id, pp.admission_id, a.admission_number, pp.medical_procedure_id, pp.performed_by_doctor_id,
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
    if (medicalProcedureId != null) {
      sql.append(" and pp.medical_procedure_id=?");
      args.add(medicalProcedureId);
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
              record.put("admissionNumber", rs.getString("admission_number"));
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

  public Map<String, Object> doctorWorkload(LocalDate from, LocalDate to) {
    if (from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before or equal to end date.");

    long departmentId = DepartmentContext.id();
    var zone = departmentTime.zoneId();
    var start = Timestamp.from(from.atStartOfDay(zone).toInstant());
    var end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    boolean doctorScope = actor.doctor();
    Long scopedDoctorId = doctorScope ? actor.user().getDoctorId() : null;
    if (doctorScope && scopedDoctorId == null) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("rows", List.of());
      empty.put("from", from);
      empty.put("to", to);
      empty.put("timeZone", zone.getId());
      empty.put("scope", "Your workload");
      return empty;
    }

    var sql = new StringBuilder(
        """
        with active_admissions as (
          select department_id, attending_doctor_id doctor_id, count(*) active_admissions
          from admissions
          where department_id=? and status='ACTIVE'
          group by department_id, attending_doctor_id
        ), assigned_beds as (
          select a.department_id, a.attending_doctor_id doctor_id, count(ra.id) assigned_beds
          from room_assignments ra
          join admissions a on a.id=ra.admission_id and a.department_id=ra.department_id
          where ra.department_id=? and ra.released_at is null and a.status='ACTIVE'
          group by a.department_id, a.attending_doctor_id
        ), recent_procedures as (
          select pp.department_id, pp.performed_by_doctor_id doctor_id, count(*) recent_procedures
          from performed_procedures pp
          join admissions a on a.id=pp.admission_id and a.department_id=pp.department_id
          where pp.department_id=? and pp.performed_at>=? and pp.performed_at<?
        """);
    var args = new ArrayList<Object>();
    args.add(departmentId);
    args.add(departmentId);
    args.add(departmentId);
    args.add(start);
    args.add(end);
    if (doctorScope) {
      sql.append(" and a.attending_doctor_id=?");
      args.add(scopedDoctorId);
    }
    sql.append(
        """
          group by pp.department_id, pp.performed_by_doctor_id
        )
        select d.id doctor_id, d.version doctor_version, d.doctor_identifier,
               d.first_name, d.last_name, d.specialty,
               coalesce(aa.active_admissions, 0) active_admissions,
               coalesce(ab.assigned_beds, 0) assigned_beds,
               coalesce(rp.recent_procedures, 0) recent_procedures
        from doctors d
        left join active_admissions aa on aa.department_id=d.department_id and aa.doctor_id=d.id
        left join assigned_beds ab on ab.department_id=d.department_id and ab.doctor_id=d.id
        left join recent_procedures rp on rp.department_id=d.department_id and rp.doctor_id=d.id
        where d.department_id=? and d.active=true
        """);
    args.add(departmentId);
    if (doctorScope) {
      sql.append(" and d.id=?");
      args.add(scopedDoctorId);
    }
    sql.append(" order by lower(d.last_name), lower(d.first_name), d.id");

    List<Map<String, Object>> rows =
        jdbc.query(
            sql.toString(),
            (rs, n) -> {
              Map<String, Object> doctor = new LinkedHashMap<>();
              doctor.put("id", rs.getLong("doctor_id"));
              doctor.put("version", rs.getLong("doctor_version"));
              doctor.put("doctorIdentifier", rs.getString("doctor_identifier"));
              doctor.put("firstName", rs.getString("first_name"));
              doctor.put("lastName", rs.getString("last_name"));
              doctor.put("specialty", rs.getString("specialty"));
              doctor.put("active", true);
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("doctor", doctor);
              row.put("activeAdmissions", rs.getLong("active_admissions"));
              row.put("assignedBeds", rs.getLong("assigned_beds"));
              row.put("recentProcedures", rs.getLong("recent_procedures"));
              return row;
            },
            args.toArray());

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("rows", rows);
    report.put("from", from);
    report.put("to", to);
    report.put("timeZone", zone.getId());
    report.put("scope", doctorScope ? "Your workload" : "Department");
    return report;
  }

  /**
   * Reports room bed utilization over half-open assignment intervals. Each assignment occupies
   * one bed from assigned_at inclusive to released_at exclusive; open assignments end at the
   * earlier of the exclusive report end and the current instant. Bucket boundaries use the
   * department time zone, and the first and last buckets are clipped to the requested dates.
   * Capacity uses each room's current configured bed count from its creation time because prior
   * bed-count changes are not versioned.
   */
  public Map<String, Object> roomUtilization(
      LocalDate from, LocalDate to, String bucket, Long roomId, Long patientId) {
    if (from == null || to == null || from.isAfter(to))
      throw new ApiException(400, "INVALID_PERIOD", "Start date must be before or equal to end date.");
    if (!"day".equals(bucket) && !"week".equals(bucket))
      throw new ApiException(400, "INVALID_BUCKET", "Bucket must be day or week.");
    if (ChronoUnit.DAYS.between(from, to) >= 366)
      throw new ApiException(400, "PERIOD_TOO_LARGE", "Date range must not exceed 366 days.");

    var currentActor = actor.user();
    if (patientId != null) hospital.accessible(patientId);
    Long scopedPatientId = patientId;
    if ("PATIENT".equals(currentActor.getRole())) {
      scopedPatientId = currentActor.getPatientId();
      if (scopedPatientId == null)
        throw new org.springframework.security.access.AccessDeniedException("Patient record unavailable");
      hospital.accessible(scopedPatientId);
    }
    boolean doctorScope = actor.doctor();
    Long scopedDoctorId = doctorScope ? currentActor.getDoctorId() : null;
    if (doctorScope && scopedDoctorId == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "Doctor account is not linked to this department");
    long departmentId = DepartmentContext.id();
    var zone = departmentTime.zoneId();
    Instant requestedStart = from.atStartOfDay(zone).toInstant();
    Instant requestedEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
    Instant now = Instant.now();
    Instant effectiveEnd = requestedEnd.isBefore(now) ? requestedEnd : now;

    var roomsSql = new StringBuilder(
        "select id, room_number, bed_count, created_at from rooms where department_id=?");
    var roomArgs = new ArrayList<Object>();
    roomArgs.add(departmentId);
    if (roomId != null) {
      roomsSql.append(" and id=?");
      roomArgs.add(roomId);
    }
    roomsSql.append(" order by lower(room_number), id");
    var roomRows = jdbc.query(roomsSql.toString(), (rs, n) -> {
      Map<String, Object> room = new LinkedHashMap<>();
      room.put("roomId", rs.getLong("id"));
      room.put("roomNumber", rs.getString("room_number"));
      room.put("bedCount", rs.getInt("bed_count"));
      room.put("createdAt", rs.getTimestamp("created_at").toInstant());
      return room;
    }, roomArgs.toArray());

    var assignmentsSql = new StringBuilder(
        """
        select ra.room_id, ra.assigned_at, ra.released_at
        from room_assignments ra
        join rooms r on r.id=ra.room_id and r.department_id=ra.department_id
        join admissions a on a.id=ra.admission_id and a.department_id=ra.department_id
        where ra.department_id=? and ra.assigned_at<?
          and (ra.released_at is null or ra.released_at>?)
        """);
    var assignmentArgs = new ArrayList<Object>();
    assignmentArgs.add(departmentId);
    assignmentArgs.add(Timestamp.from(effectiveEnd));
    assignmentArgs.add(Timestamp.from(requestedStart));
    if (roomId != null) {
      assignmentsSql.append(" and ra.room_id=?");
      assignmentArgs.add(roomId);
    }
    if (scopedPatientId != null) {
      assignmentsSql.append(" and a.patient_id=?");
      assignmentArgs.add(scopedPatientId);
    }
    if (scopedDoctorId != null) {
      assignmentsSql.append(" and a.attending_doctor_id=?");
      assignmentArgs.add(scopedDoctorId);
    }
    var occupancy = new LinkedHashMap<String, Long>();
    jdbc.query(assignmentsSql.toString(), rs -> {
      long assignedRoomId = rs.getLong("room_id");
      Instant assigned = rs.getTimestamp("assigned_at").toInstant();
      var releasedTimestamp = rs.getTimestamp("released_at");
      Instant released = releasedTimestamp == null ? effectiveEnd : releasedTimestamp.toInstant();
      if (released.isAfter(effectiveEnd)) released = effectiveEnd;
      Instant clippedStart = assigned.isBefore(requestedStart) ? requestedStart : assigned;
      Instant clippedEnd = released.isAfter(requestedEnd) ? requestedEnd : released;
      LocalDate day = clippedStart.atZone(zone).toLocalDate();
      while (day.isBefore(to.plusDays(1))) {
        LocalDate bucketStart = "day".equals(bucket)
            ? day
            : day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate segmentStartDate = bucketStart.isBefore(from) ? from : bucketStart;
        LocalDate nextBucket = "day".equals(bucket) ? bucketStart.plusDays(1) : bucketStart.plusWeeks(1);
        LocalDate segmentEndDate = nextBucket.isAfter(to.plusDays(1)) ? to.plusDays(1) : nextBucket;
        Instant segmentStart = segmentStartDate.atStartOfDay(zone).toInstant();
        Instant segmentEnd = segmentEndDate.atStartOfDay(zone).toInstant();
        Instant overlapStart = clippedStart.isAfter(segmentStart) ? clippedStart : segmentStart;
        Instant overlapEnd = clippedEnd.isBefore(segmentEnd) ? clippedEnd : segmentEnd;
        if (overlapEnd.isAfter(overlapStart)) {
          String key = assignedRoomId + ":" + segmentStartDate;
          occupancy.merge(key, ChronoUnit.MILLIS.between(overlapStart, overlapEnd), Long::sum);
        }
        day = segmentEndDate;
      }
    }, assignmentArgs.toArray());

    var rows = new ArrayList<Map<String, Object>>();
    LocalDate segmentStart = from;
    while (segmentStart.isBefore(to.plusDays(1))) {
      LocalDate bucketStart = "day".equals(bucket)
          ? segmentStart
          : segmentStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      LocalDate clippedStartDate = bucketStart.isBefore(from) ? from : bucketStart;
      LocalDate nextBucket = "day".equals(bucket) ? bucketStart.plusDays(1) : bucketStart.plusWeeks(1);
      LocalDate clippedEndDate = nextBucket.isAfter(to.plusDays(1)) ? to.plusDays(1) : nextBucket;
      Instant segmentStartInstant = clippedStartDate.atStartOfDay(zone).toInstant();
      Instant segmentEndInstant = clippedEndDate.atStartOfDay(zone).toInstant();
      for (var room : roomRows) {
        long id = (Long) room.get("roomId");
        int beds = (Integer) room.get("bedCount");
        Instant roomCreated = (Instant) room.get("createdAt");
        if (!segmentEndInstant.isAfter(roomCreated)) continue;
        Instant capacityStart = segmentStartInstant.isAfter(roomCreated) ? segmentStartInstant : roomCreated;
        Instant effectiveSegmentEnd = segmentEndInstant.isBefore(effectiveEnd) ? segmentEndInstant : effectiveEnd;
        double roomCapacityHours = effectiveSegmentEnd.isAfter(capacityStart)
            ? ChronoUnit.MILLIS.between(capacityStart, effectiveSegmentEnd) / 3_600_000d
            : 0d;
        double occupiedHours = occupancy.getOrDefault(id + ":" + clippedStartDate, 0L) / 3_600_000d;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("periodStart", clippedStartDate);
        row.put("periodEnd", clippedEndDate.minusDays(1));
        row.put("roomId", id);
        row.put("roomNumber", room.get("roomNumber"));
        row.put("bedCount", beds);
        row.put("occupiedBedHours", roundHours(occupiedHours));
        row.put("capacityBedHours", roundHours(roomCapacityHours * beds));
        row.put("utilizationPercent", roomCapacityHours <= 0 || beds == 0
            ? 0d : roundPercent(occupiedHours / (roomCapacityHours * beds) * 100d));
        rows.add(row);
      }
      segmentStart = clippedEndDate;
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("rows", rows);
    report.put("from", from);
    report.put("to", to);
    report.put("bucket", bucket);
    report.put("timeZone", zone.getId());
    report.put("scope", scopedPatientId != null ? "Patient" : doctorScope ? "Your assigned admissions" : "Department");
    report.put("capacityBasis", "Current configured bed count applied from each room's creation time");
    return report;
  }

  private static double roundHours(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private static double roundPercent(double value) {
    return Math.round(value * 100d) / 100d;
  }
}
