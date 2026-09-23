package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.Views;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OperationsService {
  private final HospitalService hospital;
  private final AuditEventRepository audit;
  private final AdmissionRepository admissions;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final AuditService auditService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final DepartmentTimeService departmentTime;
  private final int longStayDays;
  private final double warning, critical;

  public OperationsService(HospitalService hospital, AuditEventRepository audit,
      AdmissionRepository admissions, WorkflowLockRepository lock, Actor actor, AuditService auditService,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      DepartmentTimeService departmentTime,
      @Value("${app.operations.long-stay-days:7}") int longStayDays,
      @Value("${app.operations.warning-percent:75}") double warning,
      @Value("${app.operations.critical-percent:90}") double critical) {
    this.hospital = hospital; this.audit = audit; this.admissions = admissions;
    this.lock = lock; this.actor = actor; this.auditService = auditService; this.jdbc = jdbc;
    this.departmentTime = departmentTime;
    this.longStayDays = longStayDays; this.warning = warning; this.critical = critical;
  }

  public Map<String, Object> overview() {
    var now = Instant.now();
    var zone = departmentTime.zoneId();
    var today = LocalDate.ofInstant(now, zone);
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    Long doctorId = actor.doctor() ? actor.user().getDoctorId() : null;
    var trends = new ArrayList<Map<String, Object>>();
    for (int i = 13; i >= 0; i--) {
      var day = today.minusDays(i);
      var start = java.sql.Timestamp.from(day.atStartOfDay(zone).toInstant());
      var endExclusive = java.sql.Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant());
      var end = java.sql.Timestamp.from(now);
      String admissionWindow = i == 0
          ? "admission_date_time>=? and admission_date_time<=?"
          : "admission_date_time>=? and admission_date_time<?";
      String dischargeWindow = i == 0
          ? "discharge_date_time>=? and discharge_date_time<=?"
          : "discharge_date_time>=? and discharge_date_time<?";
      Long admitted = doctorId == null
          ? jdbc.queryForObject("select count(*) from admissions where department_id=? and " + admissionWindow, Long.class, departmentId, start, i == 0 ? end : endExclusive)
          : jdbc.queryForObject("select count(*) from admissions where department_id=? and " + admissionWindow + " and attending_doctor_id=?", Long.class, departmentId, start, i == 0 ? end : endExclusive, doctorId);
      Long discharged = doctorId == null
          ? jdbc.queryForObject("select count(*) from admissions where department_id=? and " + dischargeWindow, Long.class, departmentId, start, i == 0 ? end : endExclusive)
          : jdbc.queryForObject("select count(*) from admissions where department_id=? and " + dischargeWindow + " and attending_doctor_id=?", Long.class, departmentId, start, i == 0 ? end : endExclusive, doctorId);
      var censusCutoff = i == 0 ? end : endExclusive;
      String occupancyCondition = i == 0
          ? "admission_date_time<=? and (discharge_date_time is null or discharge_date_time>?)"
          : "admission_date_time<? and (discharge_date_time is null or discharge_date_time>=?)";
      Long occupied = doctorId == null
          ? jdbc.queryForObject("select count(*) from admissions where department_id=? and " + occupancyCondition, Long.class, departmentId, censusCutoff, censusCutoff)
          : jdbc.queryForObject("select count(*) from admissions where department_id=? and " + occupancyCondition + " and attending_doctor_id=?", Long.class, departmentId, censusCutoff, censusCutoff, doctorId);
      trends.add(Map.of("date", day.toString(),
          "admissions", admitted == null ? 0 : admitted,
          "discharges", discharged == null ? 0 : discharged,
          "occupied", occupied == null ? 0 : occupied));
    }
    var ids = doctorId == null
        ? jdbc.queryForList("select id from admissions where department_id=?", Long.class, departmentId)
        : jdbc.queryForList("select id from admissions where department_id=? and attending_doctor_id=?", Long.class, departmentId, doctorId);
    var recent = ids.isEmpty() ? List.of() : audit.findAll(org.springframework.data.domain.Sort.by("timestamp").descending()).stream()
        .filter(e -> "Admission".equals(e.getEntityType()) && ids.contains(e.getEntityId()))
        .limit(12).map(e -> Map.of("id", e.getId(), "eventType", e.getEventType(),
            "timestamp", e.getTimestamp(), "admissionId", e.getEntityId(), "source", e.getSource())).toList();
    Long longStay = doctorId == null
        ? jdbc.queryForObject("select count(*) from admissions where department_id=? and status='ACTIVE' and admission_date_time<?", Long.class, departmentId, java.sql.Timestamp.from(now.minusSeconds(longStayDays * 86400L)))
        : jdbc.queryForObject("select count(*) from admissions where department_id=? and status='ACTIVE' and admission_date_time<? and attending_doctor_id=?", Long.class, departmentId, java.sql.Timestamp.from(now.minusSeconds(longStayDays * 86400L)), doctorId);
    Double averageStay = doctorId == null
        ? jdbc.queryForObject("select coalesce(avg(extract(epoch from (now() - admission_date_time))/86400.0),0) from admissions where department_id=? and status='ACTIVE'", Double.class, departmentId)
        : jdbc.queryForObject("select coalesce(avg(extract(epoch from (now() - admission_date_time))/86400.0),0) from admissions where department_id=? and status='ACTIVE' and attending_doctor_id=?", Double.class, departmentId, doctorId);
    Long expected = doctorId == null
        ? jdbc.queryForObject("select count(*) from admissions where department_id=? and status='ACTIVE' and expected_discharge_date=?", Long.class, departmentId, today)
        : jdbc.queryForObject("select count(*) from admissions where department_id=? and status='ACTIVE' and expected_discharge_date=? and attending_doctor_id=?", Long.class, departmentId, today, doctorId);
    var overdueDischarges = overdueDischarges(departmentId, doctorId, today);
    return Map.of("trends", trends, "activity", recent,
        "averageStayDays", averageStay == null ? 0 : averageStay,
        "longStayPatients", longStay == null ? 0 : longStay,
        "expectedDischargesToday", expected == null ? 0 : expected,
        "overdueDischarges", overdueDischarges,
        "thresholds", Map.of("longStayDays", longStayDays, "warningPercent", warning, "criticalPercent", critical),
        "scope", actor.doctor() ? "Assigned admissions" : "Department", "timeZone", zone.getId(), "asOf", now);
  }

  private List<OverdueDischarge> overdueDischarges(long departmentId, Long doctorId, LocalDate today) {
    var sql = new StringBuilder("""
        select a.id as admission_id,
               a.admission_number,
               a.patient_id,
               p.patient_identifier,
               p.first_name || ' ' || p.last_name as patient_name,
               a.attending_doctor_id,
               d.first_name || ' ' || d.last_name as attending_doctor_name,
               a.expected_discharge_date
          from admissions a
          join patients p on p.id = a.patient_id and p.department_id = a.department_id
          join doctors d on d.id = a.attending_doctor_id and d.department_id = a.department_id
         where a.department_id = ?
           and a.status = 'ACTIVE'
           and a.expected_discharge_date < ?
        """);
    var parameters = new ArrayList<Object>();
    parameters.add(departmentId);
    parameters.add(today);
    if (doctorId != null) {
      sql.append(" and a.attending_doctor_id = ?");
      parameters.add(doctorId);
    }
    sql.append(" order by a.expected_discharge_date asc, a.id asc");
    return jdbc.query(sql.toString(), (rs, row) -> {
      var expectedDate = rs.getObject("expected_discharge_date", LocalDate.class);
      return new OverdueDischarge(
          rs.getLong("admission_id"),
          rs.getString("admission_number"),
          rs.getLong("patient_id"),
          rs.getString("patient_identifier"),
          rs.getString("patient_name"),
          rs.getLong("attending_doctor_id"),
          rs.getString("attending_doctor_name"),
          expectedDate,
          ChronoUnit.DAYS.between(expectedDate, today));
    }, parameters.toArray());
  }

  public record OverdueDischarge(
      long admissionId,
      String admissionNumber,
      long patientId,
      String patientIdentifier,
      String patientName,
      long attendingDoctorId,
      String attendingDoctorName,
      LocalDate expectedDischargeDate,
      long daysOverdue) {}

  public record ArrivalPlan(int arrivals, List<Map<String, Object>> placements, int unplaced) {}

  public ArrivalPlan simulate(int arrivals) {
    if (arrivals < 0 || arrivals > 100) throw new ApiException(400, "INVALID_SCENARIO", "Choose between 0 and 100 arrivals.");
    var rooms = hospital.rooms(0).stream().filter(r -> (boolean) r.get("active")).toList();
    var counts = new HashMap<Long, Integer>();
    rooms.forEach(r -> counts.put((Long) r.get("id"), ((Number) r.get("occupiedBeds")).intValue()));
    var placements = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < arrivals; i++) {
      var best = rooms.stream().filter(r -> counts.get((Long) r.get("id")) < ((Number) r.get("bedCount")).intValue() - ((Number) r.get("heldBeds")).intValue())
          .min(Comparator.<Map<String, Object>>comparingDouble(r -> counts.get((Long) r.get("id")) / (double) Math.max(1, ((Number) r.get("bedCount")).intValue() - ((Number) r.get("heldBeds")).intValue()))
              .thenComparing(r -> (String) r.get("roomNumber")));
      if (best.isEmpty()) break;
      var room = best.get();
      counts.merge((Long) room.get("id"), 1, Integer::sum);
      placements.add(Map.of("arrival", i + 1, "roomId", room.get("id"), "roomNumber", room.get("roomNumber")));
    }
    return new ArrivalPlan(arrivals, placements, arrivals - placements.size());
  }

  @Transactional
  public Map<String, Object> schedule(Long id, LocalDate date, Long version) {
    lock.acquire(); actor.staff();
    var a = hospital.admission(id);
    HospitalService.version(a, version);
    if (!a.getStatus().equals("ACTIVE")) throw ApiException.conflict("ADMISSION_CLOSED", "This admission is closed.");
    if (date != null && date.isBefore(departmentTime.today())) throw new ApiException(400, "INVALID_DATE", "Choose today or a future date.");
    a.setExpectedDischargeDate(date);
    admissions.saveAndFlush(a);
    auditService.log("DISCHARGE_PLANNED", "Admission", id, "UI");
    return Views.admission(a);
  }
}
