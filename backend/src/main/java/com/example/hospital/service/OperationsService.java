package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import java.time.*;
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
  private final int longStayDays;
  private final double warning, critical;

  public OperationsService(HospitalService hospital, AuditEventRepository audit,
      AdmissionRepository admissions, WorkflowLockRepository lock, Actor actor, AuditService auditService,
      @Value("${app.operations.long-stay-days:7}") int longStayDays,
      @Value("${app.operations.warning-percent:75}") double warning,
      @Value("${app.operations.critical-percent:90}") double critical) {
    this.hospital = hospital; this.audit = audit; this.admissions = admissions;
    this.lock = lock; this.actor = actor; this.auditService = auditService;
    this.longStayDays = longStayDays; this.warning = warning; this.critical = critical;
  }

  public Map<String, Object> overview() {
    var now = Instant.now();
    var today = LocalDate.now(ZoneOffset.UTC);
    var visible = hospital.admissions();
    var active = visible.stream().filter(a -> a.status.equals("ACTIVE")).toList();
    var ids = visible.stream().map(a -> a.id).collect(java.util.stream.Collectors.toSet());
    var trends = new ArrayList<Map<String, Object>>();
    for (int i = 13; i >= 0; i--) {
      var day = today.minusDays(i);
      var start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
      var end = i == 0 ? now : day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
      trends.add(Map.of("date", day.toString(),
          "admissions", visible.stream().filter(a -> !a.admissionDateTime.isBefore(start) && !a.admissionDateTime.isAfter(end)).count(),
          "discharges", visible.stream().filter(a -> a.dischargeDateTime != null && !a.dischargeDateTime.isBefore(start) && !a.dischargeDateTime.isAfter(end)).count(),
          "occupied", visible.stream().filter(a -> !a.admissionDateTime.isAfter(end) && (a.dischargeDateTime == null || a.dischargeDateTime.isAfter(end))).count()));
    }
    var recent = audit.findAll(org.springframework.data.domain.Sort.by("timestamp").descending()).stream()
        .filter(e -> "Admission".equals(e.entityType) && ids.contains(e.entityId))
        .limit(12).map(e -> Map.of("id", e.id, "eventType", e.eventType,
            "timestamp", e.timestamp, "admissionId", e.entityId, "source", e.source)).toList();
    return Map.of("trends", trends, "activity", recent,
        "averageStayDays", active.stream().mapToDouble(a -> Duration.between(a.admissionDateTime, now).toSeconds() / 86400.0).average().orElse(0),
        "longStayPatients", active.stream().filter(a -> a.admissionDateTime.isBefore(now.minusSeconds(longStayDays * 86400L))).count(),
        "expectedDischargesToday", active.stream().filter(a -> today.equals(a.expectedDischargeDate)).count(),
        "thresholds", Map.of("longStayDays", longStayDays, "warningPercent", warning, "criticalPercent", critical),
        "scope", actor.doctor() ? "Assigned admissions" : "Department", "asOf", now);
  }

  public record ArrivalPlan(int arrivals, List<Map<String, Object>> placements, int unplaced) {}

  public ArrivalPlan simulate(int arrivals) {
    if (arrivals < 0 || arrivals > 100) throw new ApiException(400, "INVALID_SCENARIO", "Choose between 0 and 100 arrivals.");
    var rooms = hospital.rooms(0).stream().filter(r -> (boolean) r.get("active")).toList();
    var counts = new HashMap<Long, Integer>();
    rooms.forEach(r -> counts.put((Long) r.get("id"), ((Number) r.get("occupiedBeds")).intValue()));
    var placements = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < arrivals; i++) {
      var best = rooms.stream().filter(r -> counts.get((Long) r.get("id")) < ((Number) r.get("bedCount")).intValue())
          .min(Comparator.<Map<String, Object>>comparingDouble(r -> counts.get((Long) r.get("id")) / ((Number) r.get("bedCount")).doubleValue())
              .thenComparing(r -> (String) r.get("roomNumber")));
      if (best.isEmpty()) break;
      var room = best.get();
      counts.merge((Long) room.get("id"), 1, Integer::sum);
      placements.add(Map.of("arrival", i + 1, "roomId", room.get("id"), "roomNumber", room.get("roomNumber")));
    }
    return new ArrivalPlan(arrivals, placements, arrivals - placements.size());
  }

  @Transactional
  public Admission schedule(Long id, LocalDate date, Long version) {
    lock.acquire(); actor.staff();
    var a = hospital.admission(id);
    HospitalService.version(a, version);
    if (!a.status.equals("ACTIVE")) throw ApiException.conflict("ADMISSION_CLOSED", "This admission is closed.");
    if (date != null && date.isBefore(LocalDate.now(ZoneOffset.UTC))) throw new ApiException(400, "INVALID_DATE", "Choose today or a future date.");
    a.expectedDischargeDate = date;
    admissions.saveAndFlush(a);
    auditService.log("DISCHARGE_PLANNED", "Admission", id, "UI");
    return a;
  }
}
