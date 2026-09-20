package com.example.hospital.service;

import com.example.hospital.domain.AppUser;
import com.example.hospital.repository.AppUserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements CommandLineRunner {
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final boolean seed;
  private final String password;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final ObjectProvider<DemoSeed> demo;

  public Bootstrap(
      AppUserRepository u,
      PasswordEncoder e,
      @Value("${app.seed}") boolean seed,
      @Value("${app.bootstrap-password}") String password,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      ObjectProvider<DemoSeed> demo) {
    users = u;
    encoder = e;
    this.seed = seed;
    this.password = password;
    this.jdbc = jdbc;
    this.demo = demo;
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
    admin.username = "admin";
    admin.passwordHash = encoder.encode(password);
    admin.role = "ADMIN";
    users.save(admin);
    enroll(admin);
    if (seed) demo.ifAvailable(d -> d.populate(admin, password, encoder));
  }

  private void enroll(AppUser user) {
    users.flush();
    Long hospitalId =
        jdbc.queryForObject(
            "select id from hospitals where name=? order by id limit 1", Long.class, "Medcore Hospital");
    if (hospitalId == null)
      hospitalId = jdbc.queryForObject("select id from hospitals order by id limit 1", Long.class);
    Long departmentId =
        jdbc.queryForObject(
            "select id from departments where hospital_id=? order by id limit 1", Long.class, hospitalId);
    jdbc.update(
        "insert into hospital_memberships(hospital_id,user_id,owner) values (?,?,?) on conflict do nothing",
        hospitalId,
        user.id,
        "ADMIN".equals(user.role));
    jdbc.update(
        "insert into department_memberships(department_id,user_id,role,doctor_id) values (?,?,?,?) on conflict do nothing",
        departmentId,
        user.id,
        user.role,
        user.doctorId);
  }
}
