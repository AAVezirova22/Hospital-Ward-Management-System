package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceService {
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, List<Instant>> joinAttempts = new ConcurrentHashMap<>();
  public WorkspaceService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
  }
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
      throw new ApiException(403, "HOSPITAL_OWNER_REQUIRED", "Only a hospital owner can manage this hospital.");
  }

  @Transactional
  public Map<String, Object> createHospital(String hospitalName, String departmentName) {
    String hospital = name(hospitalName);
    if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from hospitals where lower(name)=lower(?)", Boolean.class, hospital)))
      throw ApiException.conflict("HOSPITAL_NAME_TAKEN", "A hospital with this name already exists.");
    long id = jdbc.queryForObject("insert into hospitals(name,join_code) values (?,?) returning id", Long.class, hospital, code("H-"));
    jdbc.update("insert into hospital_memberships(hospital_id,user_id,owner) values (?,?,true)", id, actor.user().id);
    var department = createDepartment(id, departmentName);
    audit.log("HOSPITAL_CREATED", "Hospital", id, "UI");
    String hospitalCode = jdbc.queryForObject("select join_code from hospitals where id=?", String.class, id);
    return Map.of(
        "hospitalId", id,
        "departmentId", department.get("departmentId"),
        "hospitalCode", hospitalCode,
        "departmentCode", department.get("joinCode"));
  }

  @Transactional
  public Map<String, Object> createDepartment(long hospitalId, String departmentName) {
    owner(hospitalId);
    long id = jdbc.queryForObject("insert into departments(hospital_id,name,join_code) values (?,?,?) returning id", Long.class, hospitalId, name(departmentName), code("D-"));
    jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'ADMIN')", id, actor.user().id);
    jdbc.update("insert into workflow_lock(id) values (?) on conflict do nothing", id);
    audit.log("DEPARTMENT_CREATED", "Department", id, "UI");
    String joinCode = jdbc.queryForObject("select join_code from departments where id=?", String.class, id);
    return Map.of("departmentId", id, "joinCode", joinCode);
  }

  @Transactional
  public Map<String, Object> join(String supplied) {
    return join(supplied, null);
  }

  @Transactional
  public Map<String, Object> join(String supplied, String remoteAddr) {
    String code = supplied == null ? "" : supplied.strip().toUpperCase(Locale.ROOT);
    if (!code.matches("(?:[HD]-)?[A-F0-9]{24,32}"))
      throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
    var user = actor.user();
    guardJoinAttempts(user.id, remoteAddr);
    var departments = jdbc.queryForList("select id,hospital_id from departments where join_code=?", code);
    if (!departments.isEmpty()) {
      clearJoinAttempts(user.id, remoteAddr);
      long id = ((Number) departments.getFirst().get("id")).longValue();
      long hospitalId = ((Number) departments.getFirst().get("hospital_id")).longValue();
      jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", hospitalId, user.id);
      jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'MEDICAL_STAFF') on conflict do nothing", id, user.id);
      audit.log("WORKSPACE_JOINED", "Department", id, "UI");
      return Map.of("hospitalId", hospitalId, "departmentId", id);
    }
    var hospitals = jdbc.queryForList("select id from hospitals where join_code=?", Long.class, code);
    if (hospitals.isEmpty()) {
      recordJoinFailure(user.id, remoteAddr);
      audit.log("JOIN_CODE_REJECTED", "Workspace", user.id, "UI");
      throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
    }
    clearJoinAttempts(user.id, remoteAddr);
    long id = hospitals.getFirst();
    jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", id, user.id);
    audit.log("WORKSPACE_JOINED", "Hospital", id, "UI");
    String hospitalName = jdbc.queryForObject("select name from hospitals where id=?", String.class, id);
    return Map.of("hospitalId", id, "hospitalName", hospitalName == null ? "" : hospitalName);
  }

  private String joinKey(Long userId, String remoteAddr) {
    return userId + ":" + (remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr);
  }

  private void guardJoinAttempts(Long userId, String remoteAddr) {
    Instant cutoff = Instant.now().minusSeconds(600);
    var attempts = joinAttempts.computeIfAbsent(joinKey(userId, remoteAddr), k -> new ArrayList<>());
    synchronized (attempts) {
      attempts.removeIf(t -> t.isBefore(cutoff));
      if (attempts.size() >= 5)
        throw new ApiException(429, "RATE_LIMITED", "Too many invalid join codes. Wait a few minutes and try again.");
    }
  }

  private void recordJoinFailure(Long userId, String remoteAddr) {
    var attempts = joinAttempts.computeIfAbsent(joinKey(userId, remoteAddr), k -> new ArrayList<>());
    synchronized (attempts) {
      attempts.add(Instant.now());
    }
  }

  private void clearJoinAttempts(Long userId, String remoteAddr) {
    joinAttempts.remove(joinKey(userId, remoteAddr));
  }

  @Transactional
  public String rotate(boolean hospital, long id) {
    if (hospital) owner(id);
    else if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from department_memberships where department_id=? and user_id=? and role='ADMIN'", Boolean.class, id, actor.user().id)))
      throw new ApiException(403, "DEPARTMENT_ADMIN_REQUIRED", "Only a department administrator can replace its code.");
    String table = hospital ? "hospitals" : "departments";
    for (int attempt = 0; attempt < 8; attempt++) {
      String next = code(hospital ? "H-" : "D-");
      try {
        jdbc.execute((ConnectionCallback<Void>) con -> {
          var savepoint = con.setSavepoint("join_code");
          try (var statement = con.prepareStatement("update " + table + " set join_code=? where id=?")) {
            statement.setString(1, next);
            statement.setLong(2, id);
            statement.executeUpdate();
            con.releaseSavepoint(savepoint);
          } catch (java.sql.SQLException e) {
            con.rollback(savepoint);
            throw e;
          }
          return null;
        });
        audit.log("JOIN_CODE_ROTATED", hospital ? "Hospital" : "Department", id, "UI");
        return next;
      } catch (DuplicateKeyException | DataIntegrityViolationException e) {
        if (attempt == 7)
          throw new ApiException(409, "JOIN_CODE_COLLISION", "Could not allocate a unique join code. Try again.");
      }
    }
    throw new ApiException(409, "JOIN_CODE_COLLISION", "Could not allocate a unique join code. Try again.");
  }

  private boolean hospitalOwner(long hospitalId, long userId) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=? and owner=true",
        Boolean.class, hospitalId, userId));
  }

  private long ownerCount(long hospitalId) {
    Long count = jdbc.queryForObject(
        "select count(*) from hospital_memberships where hospital_id=? and owner=true", Long.class, hospitalId);
    return count == null ? 0 : count;
  }

  private void departmentAdmin(long departmentId) {
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from department_memberships where department_id=? and user_id=? and role='ADMIN'",
        Boolean.class, departmentId, actor.user().id))) return;
    Long hospitalId = jdbc.queryForObject("select hospital_id from departments where id=?", Long.class, departmentId);
    if (hospitalId == null) throw new ApiException(404, "NOT_FOUND", "Department not found.");
    owner(hospitalId);
  }

  @Transactional
  public void leaveDepartment(long departmentId) {
    long userId = actor.user().id;
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from department_memberships where department_id=? and user_id=?", Boolean.class, departmentId, userId)))
      throw new ApiException(404, "NOT_FOUND", "You are not a member of this department.");
    jdbc.update("delete from department_memberships where department_id=? and user_id=?", departmentId, userId);
    audit.log("DEPARTMENT_LEFT", "Department", departmentId, "UI");
  }

  @Transactional
  public void leaveHospital(long hospitalId) {
    long userId = actor.user().id;
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=?", Boolean.class, hospitalId, userId)))
      throw new ApiException(404, "NOT_FOUND", "You are not a member of this hospital.");
    if (hospitalOwner(hospitalId, userId) && ownerCount(hospitalId) <= 1)
      throw ApiException.conflict("LAST_OWNER", "Transfer hospital ownership before leaving.");
    jdbc.update("delete from department_memberships where user_id=? and department_id in (select id from departments where hospital_id=?)", userId, hospitalId);
    jdbc.update("delete from hospital_memberships where hospital_id=? and user_id=?", hospitalId, userId);
    audit.log("HOSPITAL_LEFT", "Hospital", hospitalId, "UI");
  }

  @Transactional
  public void revokeDepartment(long departmentId, long userId) {
    departmentAdmin(departmentId);
    if (userId == actor.user().id) {
      leaveDepartment(departmentId);
      return;
    }
    jdbc.update("delete from department_memberships where department_id=? and user_id=?", departmentId, userId);
    audit.log("DEPARTMENT_MEMBER_REVOKED", "Department", departmentId, "UI");
  }

  @Transactional
  public void revokeHospital(long hospitalId, long userId) {
    owner(hospitalId);
    if (hospitalOwner(hospitalId, userId) && ownerCount(hospitalId) <= 1)
      throw ApiException.conflict("LAST_OWNER", "Transfer hospital ownership before removing the last owner.");
    jdbc.update("delete from department_memberships where user_id=? and department_id in (select id from departments where hospital_id=?)", userId, hospitalId);
    jdbc.update("delete from hospital_memberships where hospital_id=? and user_id=?", hospitalId, userId);
    audit.log("HOSPITAL_MEMBER_REVOKED", "Hospital", hospitalId, "UI");
  }

  @Transactional
  public void grantOwner(long hospitalId, long userId) {
    owner(hospitalId);
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=?", Boolean.class, hospitalId, userId)))
      throw new ApiException(400, "NOT_A_MEMBER", "That account must join the hospital before becoming an owner.");
    jdbc.update("update hospital_memberships set owner=true where hospital_id=? and user_id=?", hospitalId, userId);
    audit.log("HOSPITAL_OWNER_GRANTED", "Hospital", hospitalId, "UI");
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
