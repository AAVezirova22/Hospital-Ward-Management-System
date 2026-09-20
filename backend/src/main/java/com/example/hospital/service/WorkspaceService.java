package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceService {
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final SecureRandom random = new SecureRandom();
  public WorkspaceService(JdbcTemplate jdbc, Actor actor) { this.jdbc = jdbc; this.actor = actor; }
  public record Department(long id, String name, String role, String joinCode) {}
  public record Hospital(long id, String name, boolean owner, String joinCode, List<Department> departments) {}

  public List<Hospital> list() {
    var user = actor.user();
    return jdbc.query("select h.id,h.name,m.owner,h.join_code from hospitals h join hospital_memberships m on m.hospital_id=h.id where m.user_id=? order by h.name,h.id",
        (rs, n) -> {
          long id = rs.getLong(1); boolean owner = rs.getBoolean(3);
          var departments = jdbc.query("select d.id,d.name,m.role,d.join_code from departments d join department_memberships m on m.department_id=d.id where d.hospital_id=? and m.user_id=? order by d.name,d.id",
              (d, i) -> new Department(d.getLong(1), d.getString(2), d.getString(3),
                  "ADMIN".equals(d.getString(3)) ? d.getString(4) : null), id, user.id);
          return new Hospital(id, rs.getString(2), owner, owner ? rs.getString(4) : null, departments);
        }, user.id);
  }

  private String code(String prefix) {
    byte[] bytes = new byte[12]; random.nextBytes(bytes);
    return prefix + HexFormat.of().withUpperCase().formatHex(bytes);
  }
  private String name(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120)
      throw new ApiException(400, "INVALID_NAME", "Use a name between 1 and 120 characters.");
    return value.strip();
  }
  private void owner(long hospitalId) {
    if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=? and owner=true", Boolean.class, hospitalId, actor.user().id)))
      throw new ApiException(403, "HOSPITAL_OWNER_REQUIRED", "Only a hospital owner can create departments.");
  }

  @Transactional
  public long createHospital(String hospitalName, String departmentName) {
    long id = jdbc.queryForObject("insert into hospitals(name,join_code) values (?,?) returning id", Long.class, name(hospitalName), code("H-"));
    jdbc.update("insert into hospital_memberships(hospital_id,user_id,owner) values (?,?,true)", id, actor.user().id);
    return createDepartment(id, departmentName);
  }

  @Transactional
  public long createDepartment(long hospitalId, String departmentName) {
    owner(hospitalId);
    long id = jdbc.queryForObject("insert into departments(hospital_id,name,join_code) values (?,?,?) returning id", Long.class, hospitalId, name(departmentName), code("D-"));
    jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'ADMIN')", id, actor.user().id);
    return id;
  }

  @Transactional
  public Map<String, Object> join(String supplied) {
    String code = supplied == null ? "" : supplied.strip().toUpperCase(Locale.ROOT);
    if (!code.matches("(?:[HD]-)?[A-F0-9]{24,32}"))
      throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
    var user = actor.user();
    var departments = jdbc.queryForList("select id,hospital_id from departments where join_code=?", code);
    if (!departments.isEmpty()) {
      long id = ((Number) departments.getFirst().get("id")).longValue();
      long hospitalId = ((Number) departments.getFirst().get("hospital_id")).longValue();
      jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", hospitalId, user.id);
      jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'MEDICAL_STAFF') on conflict do nothing", id, user.id);
      return Map.of("hospitalId", hospitalId, "departmentId", id);
    }
    var hospitals = jdbc.queryForList("select id from hospitals where join_code=?", Long.class, code);
    if (hospitals.isEmpty()) throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
    long id = hospitals.getFirst();
    jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", id, user.id);
    return Map.of("hospitalId", id);
  }

  @Transactional
  public String rotate(boolean hospital, long id) {
    if (hospital) owner(id);
    else if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from department_memberships where department_id=? and user_id=? and role='ADMIN'", Boolean.class, id, actor.user().id)))
      throw new ApiException(403, "DEPARTMENT_ADMIN_REQUIRED", "Only a department administrator can replace its code.");
    String next = code(hospital ? "H-" : "D-");
    jdbc.update("update " + (hospital ? "hospitals" : "departments") + " set join_code=? where id=?", next, id);
    return next;
  }

  public void enroll(long userId, String role, Long doctorId) {
    long departmentId = DepartmentContext.id();
    jdbc.update("insert into hospital_memberships(hospital_id,user_id) select hospital_id,? from departments where id=? on conflict do nothing", userId, departmentId);
    jdbc.update("insert into department_memberships(department_id,user_id,role,doctor_id) values (?,?,?,?) on conflict (department_id,user_id) do update set role=excluded.role,doctor_id=excluded.doctor_id", departmentId, userId, role, doctorId);
  }

  public boolean member(long userId) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from department_memberships where department_id=? and user_id=?", Boolean.class, DepartmentContext.id(), userId));
  }
}
