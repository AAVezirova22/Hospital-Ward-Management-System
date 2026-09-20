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
      {"Vera", "Angelova"}
    };
    var allDoctors = doctors.findAll();
    var allRooms = rooms.findAll();
    for (int i = 0; i < ps.length; i++) {
      var p = new Patient();
      p.patientIdentifier = "PAT-" + String.format("%04d", i + 1);
      p.firstName = ps[i][0];
      p.lastName = ps[i][1];
      p.dateOfBirth = LocalDate.of(1960 + i * 3, 2, 12);
      p.address = "Synthetic demonstration record";
      patients.save(p);
      if (i < 9) {
        var a = new Admission();
        a.admissionNumber = "ADM-DEMO-" + (i + 1);
        a.patientId = p.id;
        a.attendingDoctorId = allDoctors.get(i % 3).id;
        a.admissionDateTime = Instant.now().minusSeconds((i + 1) * 86400L);
        a.createdBy = admin.id;
        admissions.save(a);
        var ra = new RoomAssignment();
        ra.admissionId = a.id;
        ra.roomId = allRooms.get(i / 2).id;
        ra.assignedAt = a.admissionDateTime;
        ra.createdBy = admin.id;
        ra.reason = "Initial admission";
        assignments.save(ra);
      }
    }
    String[] names = {
      "Complete blood count", "Electrocardiogram", "Ultrasound examination", "Chest X-ray"
    };
    for (int i = 0; i < names.length; i++) {
      var mp = new MedicalProcedure();
      mp.procedureCode = "PR-00" + (i + 1);
      mp.procedureName = names[i];
      mp.currentCost = new BigDecimal(25 + i * 20);
      procedures.save(mp);
    }
  }
}
