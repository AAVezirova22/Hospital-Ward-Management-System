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
      @Value("${app.bootstrap-password}") String password) {
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
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Set BOOTSTRAP_PASSWORD (12+ characters, at most 72 UTF-8 bytes) for first startup.");
    var admin = new AppUser();
    admin.username = "admin";
    admin.passwordHash = encoder.encode(password);
    admin.role = "ADMIN";
    users.save(admin);
    if (!seed) return;
    String[][] ds = {
      {"Elena", "Dimitrova", "Internal medicine"},
      {"Martin", "Ivanov", "Cardiology"},
      {"Nadia", "Petrova", "Neurology"}
    };
    for (int i = 0; i < ds.length; i++) {
      var d = new Doctor();
      d.doctorIdentifier = "DOC-00" + (i + 1);
      d.firstName = ds[i][0];
      d.lastName = ds[i][1];
      d.specialty = ds[i][2];
      doctors.save(d);
      if (i == 0) {
        var u = new AppUser();
        u.username = "doctor";
        u.role = "DOCTOR";
        u.doctorId = d.id;
        u.passwordHash = encoder.encode(password);
        users.save(u);
      }
    }
    var staff = new AppUser();
    staff.username = "staff";
    staff.role = "MEDICAL_STAFF";
    staff.passwordHash = encoder.encode(password);
    users.save(staff);
    for (int i = 0; i < 8; i++) {
      var r = new Room();
      r.roomNumber = "" + (301 + i);
      r.bedCount = i < 4 ? 4 : 2;
      r.active = i != 7;
      rooms.save(r);
    }
    String[][] ps = {
      {"Ivan", "Petrov"},
      {"Mila", "Georgieva"},
      {"Alexander", "Kolev"},
      {"Sofia", "Ivanova"},
      {"Daniel", "Stoyanov"},
      {"Eva", "Nikolova"},
      {"Boris", "Dimitrov"},
      {"Anna", "Todorova"},
      {"Stefan", "Marinov"},
      {"Maria", "Popova"},
      {"Nikolai", "Vasilev"},
      {"Vera", "Angelova"},
      {"Lilia", "Hristova"}, {"Pavel", "Dobrev"}, {"Irina", "Mihaylova"},
      {"Victor", "Radev"}, {"Daria", "Ilieva"}, {"Emil", "Kostov"},
      {"Yana", "Pavlova"}, {"Radoslav", "Dinev"}, {"Elitsa", "Yordanova"},
      {"Kalin", "Atanasov"}, {"Nina", "Borisova"}, {"Todor", "Zhelev"},
      {"Raya", "Stankova"}, {"Plamen", "Nedev"}, {"Alina", "Markova"},
      {"Georgi", "Velikov"}
    };
    var allDoctors = doctors.findAll();
    var allRooms = rooms.findAll();
    String[] names = {"Complete blood count", "Electrocardiogram", "Ultrasound examination", "Chest X-ray"};
    String[] costs = {"27.40", "46.80", "83.50", "68.20"};
    for (int i = 0; i < names.length; i++) {
      var mp = new MedicalProcedure();
      mp.procedureCode = "PR-00" + (i + 1);
      mp.procedureName = names[i];
      mp.currentCost = new BigDecimal(costs[i]);
      procedures.save(mp);
    }
    var catalogue = procedures.findAll();
    var now = Instant.now();
    int[] placement = {2, 0, 0, 1, 1, 2, 2, 3, 3, 4, 5, 0, 1, 3};
    for (int i = 0; i < ps.length; i++) {
      var p = new Patient();
      p.patientIdentifier = "PAT-" + String.format("%04d", i + 1);
      p.firstName = ps[i][0];
      p.lastName = ps[i][1];
      p.dateOfBirth = LocalDate.of(1954 + (i * 7 % 53), 1 + i % 12, 5 + i % 23);
      p.address = "Synthetic demonstration record";
      patients.save(p);
      if (i < 26) {
        var a = new Admission();
        a.admissionNumber = "ADM-DEMO-" + (i + 1);
        a.patientId = p.id;
        a.attendingDoctorId = allDoctors.get(i % 3).id;
        a.admissionDateTime = now.minusSeconds((i < 14 ? i : 2 + (i - 14)) * 86400L + 1700 + i * 113);
        if (i >= 14) {
          a.status = "DISCHARGED";
          a.dischargeDateTime = a.admissionDateTime.plusSeconds(86400L + i * 419);
        }
        a.createdBy = admin.id;
        if (i < 14 && i % 4 == 0) a.expectedDischargeDate = LocalDate.now(ZoneOffset.UTC).plusDays(i % 3);
        admissions.save(a);
        var ra = new RoomAssignment();
        ra.admissionId = a.id;
        ra.roomId = allRooms.get(i < 14 ? placement[i] : i % 6).id;
        ra.assignedAt = a.admissionDateTime;
        ra.createdBy = admin.id;
        ra.reason = "Initial admission";
        ra.releasedAt = a.dischargeDateTime;
        assignments.save(ra);
        event(admin.id, "ADMISSION_CREATED", a.id, a.admissionDateTime);
        if (i >= 14) event(admin.id, "PATIENT_DISCHARGED", a.id, a.dischargeDateTime);
        if (i == 5 || i == 8 || i == 10) {
          // Two consecutive assignments, never overlapping, preserve the full care timeline.
          ra.roomId = allRooms.get(6).id;
          ra.releasedAt = a.admissionDateTime.plusSeconds(3600L * (i + 1));
          assignments.saveAndFlush(ra);
          var transfer = new RoomAssignment();
          transfer.admissionId = a.id;
          transfer.roomId = allRooms.get(placement[i]).id;
          transfer.assignedAt = ra.releasedAt;
          transfer.createdBy = admin.id;
          transfer.reason = "Ward capacity balancing";
          assignments.save(transfer);
          event(admin.id, "ROOM_TRANSFERRED", a.id, transfer.assignedAt);
        }
        for (int j = 0; j < 1 + i % 3; j++) {
          var mp = catalogue.get((i + j) % catalogue.size());
          var pp = new PerformedProcedure();
          pp.admissionId = a.id;
          pp.medicalProcedureId = mp.id;
          pp.performedByDoctorId = a.attendingDoctorId;
          var end = a.dischargeDateTime == null ? now : a.dischargeDateTime;
          pp.performedAt = a.admissionDateTime.plusSeconds(Duration.between(a.admissionDateTime, end).getSeconds() * (j + 1) / (2 + i % 3));
          pp.priceAtExecution = mp.currentCost;
          pp.note = "Synthetic demonstration procedure";
          performed.save(pp);
          event(admin.id, "PROCEDURE_RECORDED", a.id, pp.performedAt);
        }
      }
    }
  }

  private void event(Long userId, String type, Long admissionId, Instant time) {
    var e = new AuditEvent();
    e.userId = userId;
    e.eventType = type;
    e.entityType = "Admission";
    e.entityId = admissionId;
    e.timestamp = time;
    e.source = "DEMO";
    e.metadata = "Synthetic scenario";
    audit.save(e);
  }
}
