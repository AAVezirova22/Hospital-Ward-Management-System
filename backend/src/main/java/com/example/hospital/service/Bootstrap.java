package com.example.hospital.service;

import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements CommandLineRunner {
  private final AppUserRepository users;
  private final DoctorRepository doctors;
  private final PatientRepository patients;
  private final RoomRepository rooms;
  private final AdmissionRepository admissions;
  private final RoomAssignmentRepository assignments;
  private final MedicalProcedureRepository procedures;
  private final PerformedProcedureRepository performed;
  private final AuditEventRepository audit;
  private final PasswordEncoder encoder;
  private final boolean seed;
  private final String password;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public Bootstrap(
      AppUserRepository u,
      DoctorRepository d,
      PatientRepository p,
      RoomRepository r,
      AdmissionRepository a,
      RoomAssignmentRepository ra,
      MedicalProcedureRepository mp,
      PerformedProcedureRepository pp,
      AuditEventRepository ae,
      PasswordEncoder e,
      @Value("${app.seed}") boolean seed,
      @Value("${app.bootstrap-password}") String password,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    users = u;
    doctors = d;
    patients = p;
    rooms = r;
    admissions = a;
    assignments = ra;
    procedures = mp;
    performed = pp;
    audit = ae;
    encoder = e;
    this.seed = seed;
    this.password = password;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(String... args) {
    com.example.hospital.security.DepartmentContext.set(
        new com.example.hospital.security.DepartmentContext.Scope(1L, "ADMIN", null));
    try {
      seed();
    } finally {
      com.example.hospital.security.DepartmentContext.clear();
    }
  }

  private void seed() {
    if (users.count() > 0) return;
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Set BOOTSTRAP_PASSWORD (12+ characters, at most 72 UTF-8 bytes) for first startup.");
    var admin = new AppUser();
    admin.setUsername("admin");
    admin.setPasswordHash(encoder.encode(password));
    admin.setRole("ADMIN");
    users.save(admin);
    enroll(admin);
    if (!seed) return;
    String[][] ds = DemoScenario.DOCTORS;
    for (int i = 0; i < ds.length; i++) {
      var d = new Doctor();
      d.setDoctorIdentifier("DOC-00" + (i + 1));
      d.setFirstName(ds[i][0]);
      d.setLastName(ds[i][1]);
      d.setSpecialty(ds[i][2]);
      doctors.save(d);
      if (i == 0) {
        var u = new AppUser();
        u.setUsername("doctor");
        u.setRole("DOCTOR");
        u.setDoctorId(d.getId());
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        enroll(u);
      }
    }
    var staff = new AppUser();
    staff.setUsername("staff");
    staff.setRole("MEDICAL_STAFF");
    staff.setPasswordHash(encoder.encode(password));
    users.save(staff);
    enroll(staff);
    for (int i = 0; i < 8; i++) {
      var r = new Room();
      r.setRoomNumber("" + (301 + i));
      r.setBedCount(i < 4 ? 4 : 2);
      r.setActive(i != 7);
      rooms.save(r);
    }
    String[][] ps = DemoScenario.PATIENTS;
    var allDoctors = doctors.findAll();
    var allRooms = rooms.findAll();
    String[] names = DemoScenario.PROCEDURES;
    String[] costs = DemoScenario.PROCEDURE_COSTS;
    for (int i = 0; i < names.length; i++) {
      var mp = new MedicalProcedure();
      mp.setProcedureCode("PR-00" + (i + 1));
      mp.setProcedureName(names[i]);
      mp.setCurrentCost(new BigDecimal(costs[i]));
      procedures.save(mp);
    }
    var catalogue = procedures.findAll();
    var now = Instant.now();
    int[] placement = {2, 0, 0, 1, 1, 2, 2, 3, 3, 4, 5, 0, 1, 3};
    for (int i = 0; i < ps.length; i++) {
      var p = new Patient();
      p.setPatientIdentifier("PAT-" + String.format("%04d", i + 1));
      p.setFirstName(ps[i][0]);
      p.setLastName(ps[i][1]);
      p.setDateOfBirth(LocalDate.of(1954 + (i * 7 % 53), 1 + i % 12, 5 + i % 23));
      p.setAddress("Synthetic demonstration record");
      patients.save(p);
      if (i < 26) {
        var a = new Admission();
        a.setAdmissionNumber("ADM-DEMO-" + (i + 1));
        a.setPatientId(p.getId());
        a.setAttendingDoctorId(allDoctors.get(i % 3).getId());
        a.setAdmissionDateTime(now.minusSeconds((i < 14 ? i : 2 + (i - 14)) * 86400L + 1700 + i * 113));
        if (i >= 14) {
          a.setStatus("DISCHARGED");
          a.setDischargeDateTime(a.getAdmissionDateTime().plusSeconds(86400L + i * 419));
        }
        a.setCreatedBy(admin.getId());
        if (i < 14 && i % 4 == 0) a.setExpectedDischargeDate(LocalDate.now(ZoneOffset.UTC).plusDays(i % 3));
        admissions.save(a);
        var ra = new RoomAssignment();
        ra.setAdmissionId(a.getId());
        ra.setRoomId(allRooms.get(i < 14 ? placement[i] : i % 6).getId());
        ra.setAssignedAt(a.getAdmissionDateTime());
        ra.setCreatedBy(admin.getId());
        ra.setReason("Initial admission");
        ra.setReleasedAt(a.getDischargeDateTime());
        assignments.save(ra);
        event(admin.getId(), "ADMISSION_CREATED", a.getId(), a.getAdmissionDateTime());
        if (i >= 14) event(admin.getId(), "PATIENT_DISCHARGED", a.getId(), a.getDischargeDateTime());
        if (i == 5 || i == 8 || i == 10) {
          // Two consecutive assignments, never overlapping, preserve the full care timeline.
          ra.setRoomId(allRooms.get(6).getId());
          ra.setReleasedAt(a.getAdmissionDateTime().plusSeconds(3600L * (i + 1)));
          assignments.saveAndFlush(ra);
          var transfer = new RoomAssignment();
          transfer.setAdmissionId(a.getId());
          transfer.setRoomId(allRooms.get(placement[i]).getId());
          transfer.setAssignedAt(ra.getReleasedAt());
          transfer.setCreatedBy(admin.getId());
          transfer.setReason("Ward capacity balancing");
          assignments.save(transfer);
          event(admin.getId(), "ROOM_TRANSFERRED", a.getId(), transfer.getAssignedAt());
        }
        for (int j = 0; j < 1 + i % 3; j++) {
          var mp = catalogue.get((i + j) % catalogue.size());
          var pp = new PerformedProcedure();
          pp.setAdmissionId(a.getId());
          pp.setMedicalProcedureId(mp.getId());
          pp.setPerformedByDoctorId(a.getAttendingDoctorId());
          var end = a.getDischargeDateTime() == null ? now : a.getDischargeDateTime();
          pp.setPerformedAt(a.getAdmissionDateTime().plusSeconds(Duration.between(a.getAdmissionDateTime(), end).getSeconds() * (j + 1) / (2 + i % 3)));
          pp.setPriceAtExecution(mp.getCurrentCost());
          pp.setNote("Synthetic demonstration procedure");
          performed.save(pp);
          event(admin.getId(), "PROCEDURE_RECORDED", a.getId(), pp.getPerformedAt());
        }
      }
    }
  }

  private void enroll(AppUser user) {
    users.flush();
    Long hospitalId = jdbc.queryForObject("select id from hospitals where name=? order by id limit 1", Long.class, "Medcore Hospital");
    if (hospitalId == null) hospitalId = jdbc.queryForObject("select id from hospitals order by id limit 1", Long.class);
    Long departmentId = jdbc.queryForObject("select id from departments where hospital_id=? order by id limit 1", Long.class, hospitalId);
    jdbc.update("insert into hospital_memberships(hospital_id,user_id,owner) values (?,?,?) on conflict do nothing", hospitalId, user.getId(), "ADMIN".equals(user.getRole()));
    jdbc.update("insert into department_memberships(department_id,user_id,role,doctor_id) values (?,?,?,?) on conflict do nothing", departmentId, user.getId(), user.getRole(), user.getDoctorId());
  }

  private void event(Long userId, String type, Long admissionId, Instant time) {
    var e = new AuditEvent();
    e.setUserId(userId);
    e.setEventType(type);
    e.setEntityType("Admission");
    e.setEntityId(admissionId);
    e.setTimestamp(time);
    e.setSource("DEMO");
    e.setMetadata("Synthetic scenario");
    audit.save(e);
  }
}
