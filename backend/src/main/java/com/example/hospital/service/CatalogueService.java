package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.DoctorInput;
import com.example.hospital.api.ProcedureInput;
import com.example.hospital.api.PagedResult;
import com.example.hospital.api.RoomInput;
import com.example.hospital.api.Views;
import com.example.hospital.domain.Doctor;
import com.example.hospital.domain.MedicalProcedure;
import com.example.hospital.domain.Room;
import com.example.hospital.repository.AdmissionRepository;
import com.example.hospital.repository.DoctorRepository;
import com.example.hospital.repository.MedicalProcedureRepository;
import com.example.hospital.repository.RoomRepository;
import com.example.hospital.repository.RoomAssignmentRepository;
import com.example.hospital.repository.WorkflowLockRepository;
import com.example.hospital.security.Actor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogueService {
  private final HospitalService hospital;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final DoctorRepository doctors;
  private final RoomRepository rooms;
  private final MedicalProcedureRepository catalogue;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final AuditService audit;

  public CatalogueService(
      HospitalService hospital,
      WorkflowLockRepository lock,
      Actor actor,
      DoctorRepository doctors,
      RoomRepository rooms,
      MedicalProcedureRepository catalogue,
      AdmissionRepository admissions,
      AuditService audit,
      RoomAssignmentRepository assignments) {
    this.hospital = hospital;
    this.lock = lock;
    this.actor = actor;
    this.doctors = doctors;
    this.rooms = rooms;
    this.catalogue = catalogue;
    this.admissions = admissions;
    this.assignments = assignments;
    this.audit = audit;
  }

  public List<Doctor> doctors() {
    return hospital.doctors();
  }

  public List<Map<String, Object>> rooms(int minFree) {
    return rooms(minFree, List.of());
  }

  public List<Map<String, Object>> rooms(int minFree, List<String> requiredCapabilities) {
    return hospital.rooms(minFree, requiredCapabilities);
  }

  public List<MedicalProcedure> procedures() {
    return hospital.procedures();
  }

  public PagedResult<Map<String, Object>> doctorPage(
      String q, int requestedPage, int requestedSize, Boolean active) {
    boolean hasQuery = q != null && !q.isBlank();
    String query = pattern(q);
    boolean hasActive = active != null;
    boolean activeValue = Boolean.TRUE.equals(active);
    int size = safeSize(requestedSize);
    long total = doctors.countDirectory(hasQuery, query, hasActive, activeValue);
    int page = safePage(requestedPage, size, total);
    Pageable pageable =
        PageRequest.of(page, size, Sort.by("lastName", "firstName", "doctorIdentifier", "id"));
    List<Map<String, Object>> items =
        doctors.searchDirectory(hasQuery, query, hasActive, activeValue, pageable).stream()
            .map(Views::doctor)
            .toList();
    return PagedResult.of(items, page, size, total);
  }

  public PagedResult<Map<String, Object>> procedurePage(
      String q, int requestedPage, int requestedSize, Boolean active) {
    boolean hasQuery = q != null && !q.isBlank();
    String query = pattern(q);
    boolean hasActive = active != null;
    boolean activeValue = Boolean.TRUE.equals(active);
    int size = safeSize(requestedSize);
    long total = catalogue.countDirectory(hasQuery, query, hasActive, activeValue);
    int page = safePage(requestedPage, size, total);
    Pageable pageable =
        PageRequest.of(page, size, Sort.by("procedureName", "procedureCode", "id"));
    List<Map<String, Object>> items =
        catalogue.searchDirectory(hasQuery, query, hasActive, activeValue, pageable).stream()
            .map(Views::procedure)
            .toList();
    return PagedResult.of(items, page, size, total);
  }

  public PagedResult<Map<String, Object>> roomPage(
      String q,
      int requestedPage,
      int requestedSize,
      Boolean active,
      int minFree,
      Long roomId,
      List<String> requiredCapabilities) {
    // HospitalService.rooms applies the same capability, occupancy and maintenance-hold rules as
    // placement, so the directory never offers capacity an admission would be refused. A department
    // has few rooms, so the remaining filters and paging run in memory.
    String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> matching =
        hospital.rooms(minFree, requiredCapabilities).stream()
            .filter(
                room ->
                    query.isEmpty()
                        || String.valueOf(room.get("roomNumber"))
                            .toLowerCase(Locale.ROOT)
                            .contains(query))
            .filter(room -> roomId == null || roomId.equals(room.get("id")))
            .filter(room -> active == null || active.equals(room.get("active")))
            .sorted(
                Comparator.comparing((Map<String, Object> room) -> String.valueOf(room.get("roomNumber")))
                    .thenComparing(room -> (Long) room.get("id")))
            .toList();
    int size = safeSize(requestedSize);
    int page = safePage(requestedPage, size, matching.size());
    int from = Math.min(page * size, matching.size());
    return PagedResult.of(
        matching.subList(from, Math.min(from + size, matching.size())), page, size, matching.size());
  }

  private static String pattern(String q) {
    String escaped = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
    escaped = escaped.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  private static int safeSize(int size) {
    return Math.min(Math.max(size, 1), 100);
  }

  private static int safePage(int requestedPage, int size, long total) {
    if (requestedPage < 0 || total == 0) return 0;
    long lastPage = (total - 1) / size;
    return (int) Math.min(requestedPage, Math.min(lastPage, Integer.MAX_VALUE));
  }

  @Transactional
  public Doctor saveDoctor(Long id, DoctorInput in) {
    lock.acquire();
    actor.admin();
    var d = id == null ? new Doctor() : doctors.findById(id).orElseThrow(ApiException::missing);
    if (id != null) HospitalService.version(d, in.version());
    if (!in.active() && id != null && admissions.existsByAttendingDoctorIdAndStatus(id, "ACTIVE"))
      throw ApiException.conflict(
          "DOCTOR_HAS_PATIENTS", "Reassign active admissions before deactivating this doctor.");
    d.setDoctorIdentifier(in.doctorIdentifier().trim());
    d.setFirstName(in.firstName().trim());
    d.setLastName(in.lastName().trim());
    d.setSpecialty(in.specialty().trim());
    d.setActive(in.active());
    doctors.saveAndFlush(d);
    audit.log("DOCTOR_SAVED", "Doctor", d.getId(), "UI");
    return d;
  }

  @Transactional
  public Room saveRoom(Long id, RoomInput in) {
    lock.acquire();
    actor.admin();
    var r = id == null ? new Room() : hospital.room(id);
    if (id != null) HospitalService.version(r, in.version());
    long used = id == null ? 0 : hospital.occupied(id);
    long held = id == null ? 0 : hospital.held(id);
    if (in.bedCount() < used + held || (!in.active() && used + held > 0))
      throw ApiException.conflict(
          "ROOM_OCCUPIED",
          "The room has occupied or reserved beds; release capacity before reducing it or deactivating"
              + " it.");
    r.setRoomNumber(in.roomNumber().trim());
    r.setBedCount(in.bedCount());
    r.setActive(in.active());
    var capabilities =
        in.capabilities() == null
            ? (id == null ? java.util.Set.<String>of() : r.getCapabilities())
            : RoomCapabilityMatcher.normalize(in.capabilities());
    if (id != null) {
      var requiredInUse =
          assignments.findByRoomIdAndReleasedAtIsNull(id).stream()
              .map(assignment -> admissions.findById(assignment.getAdmissionId()).orElseThrow())
              .filter(admission -> admission.getStatus().equals("ACTIVE"))
              .flatMap(admission -> admission.getRequiredRoomCapabilities().stream())
              .filter(capability -> !capabilities.contains(capability))
              .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
      if (!requiredInUse.isEmpty())
        throw ApiException.conflict(
            "ROOM_CAPABILITY_IN_USE",
            "Active admissions in this room require capabilities being removed: "
                + String.join(", ", requiredInUse)
                + ".");
    }
    r.setCapabilities(capabilities);
    rooms.saveAndFlush(r);
    audit.log("ROOM_SAVED", "Room", r.getId(), "UI");
    return r;
  }

  @Transactional
  public MedicalProcedure saveProcedure(Long id, ProcedureInput in) {
    actor.admin();
    var p =
        id == null
            ? new MedicalProcedure()
            : catalogue.findById(id).orElseThrow(ApiException::missing);
    if (id != null) HospitalService.version(p, in.version());
    p.setProcedureCode(in.procedureCode().trim());
    p.setProcedureName(in.procedureName().trim());
    p.setCurrentCost(in.currentCost());
    p.setActive(in.active());
    catalogue.saveAndFlush(p);
    audit.log("PROCEDURE_SAVED", "MedicalProcedure", p.getId(), "UI");
    return p;
  }
}
