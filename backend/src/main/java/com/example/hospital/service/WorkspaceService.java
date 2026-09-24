package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.api.PagedResult;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
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
  public record Department(long id, String name, String role, boolean hasJoinCode, String timeZone) {}
  public record Hospital(long id, String name, boolean owner, boolean hasJoinCode, List<Department> departments) {}
  public record DepartmentRole(long departmentId, String departmentName, String role, Long doctorId,
      String doctorIdentifier, String doctorName, Instant joinedAt) {}
  public record HospitalMember(long userId, String username, String role, Instant joinedAt,
      boolean enabled, List<DepartmentRole> departments) {}
  public record DepartmentMember(long userId, String username, String role, Long doctorId,
      String doctorIdentifier, String doctorName, Instant joinedAt, boolean enabled) {}
  private record HospitalMemberBase(long userId, String username, String role, Instant joinedAt,
      boolean enabled) {}

  public List<Hospital> list() {
    var user = actor.user();
    return jdbc.query("select h.id,h.name,m.owner from hospitals h join hospital_memberships m on m.hospital_id=h.id where m.user_id=? order by h.name,h.id",
        (rs, n) -> {
          long id = rs.getLong(1); boolean owner = rs.getBoolean(3);
          var departments = jdbc.query("select d.id,d.name,m.role,d.time_zone from departments d join department_memberships m on m.department_id=d.id where d.hospital_id=? and m.user_id=? order by d.name,d.id",
              (d, i) -> new Department(d.getLong(1), d.getString(2), d.getString(3),
                  "ADMIN".equals(d.getString(3)), d.getString(4)), id, user.getId());
          return new Hospital(id, rs.getString(2), owner, owner, departments);
        }, user.getId());
  }

  public PagedResult<HospitalMember> hospitalMembers(long hospitalId, int requestedPage, int requestedSize) {
    owner(hospitalId);
    int size = Math.min(Math.max(requestedSize, 1), 100);
    long total = jdbc.queryForObject(
        "select count(*) from hospital_memberships where hospital_id=?", Long.class, hospitalId);
    int page = safePage(requestedPage, size, total);
    long offset = (long) page * size;
    var members = jdbc.query(
        "select m.user_id,u.username,m.owner,m.joined_at,u.enabled from hospital_memberships m join app_users u on u.id=m.user_id where m.hospital_id=? order by m.joined_at desc nulls last,m.user_id desc limit ? offset ?",
        (rs, row) -> new HospitalMemberBase(rs.getLong(1), rs.getString(2),
            rs.getBoolean(3) ? "OWNER" : "MEMBER",
            rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getBoolean(5)),
        hospitalId, size, offset);
    var departmentRoles = hospitalDepartmentRoles(hospitalId, members);
    var items = members.stream().map(member -> new HospitalMember(member.userId(), member.username(),
        member.role(), member.joinedAt(), member.enabled(), departmentRoles.getOrDefault(member.userId(), List.of()))).toList();
    return PagedResult.of(items, page, size, total);
  }

  public PagedResult<DepartmentMember> departmentMembers(long departmentId, int requestedPage, int requestedSize) {
    departmentAdmin(departmentId);
    int size = Math.min(Math.max(requestedSize, 1), 100);
    long total = jdbc.queryForObject(
        "select count(*) from department_memberships where department_id=?", Long.class, departmentId);
    int page = safePage(requestedPage, size, total);
    long offset = (long) page * size;
    var items = jdbc.query(
        "select dm.user_id,u.username,dm.role,dm.doctor_id,d.doctor_identifier,concat_ws(' ',d.first_name,d.last_name),dm.joined_at,u.enabled from department_memberships dm join app_users u on u.id=dm.user_id left join doctors d on d.id=dm.doctor_id where dm.department_id=? order by dm.joined_at desc nulls last,dm.user_id desc limit ? offset ?",
        (rs, row) -> new DepartmentMember(rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getObject(4, Long.class), rs.getString(5), rs.getString(6),
            rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), rs.getBoolean(8)),
        departmentId, size, offset);
    return PagedResult.of(items, page, size, total);
  }

  private Map<Long, List<DepartmentRole>> hospitalDepartmentRoles(long hospitalId, List<HospitalMemberBase> members) {
    if (members.isEmpty()) return Map.of();
    String placeholders = String.join(",", Collections.nCopies(members.size(), "?"));
    String sql = "select dm.user_id,dm.department_id,d.name,dm.role,dm.doctor_id,doc.doctor_identifier,concat_ws(' ',doc.first_name,doc.last_name),dm.joined_at "
        + "from department_memberships dm join departments d on d.id=dm.department_id left join doctors doc on doc.id=dm.doctor_id "
        + "where d.hospital_id=? and dm.user_id in (" + placeholders + ") order by dm.user_id,d.name,d.id";
    var params = new ArrayList<Object>();
    params.add(hospitalId);
    members.forEach(member -> params.add(member.userId()));
    var grouped = new LinkedHashMap<Long, List<DepartmentRole>>();
    jdbc.query(sql, rs -> {
      long userId = rs.getLong(1);
      grouped.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(new DepartmentRole(
          rs.getLong(2), rs.getString(3), rs.getString(4), rs.getObject(5, Long.class),
          rs.getString(6), rs.getString(7),
          rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()));
    }, params.toArray());
    return grouped;
  }

  private static int safePage(int requestedPage, int size, long total) {
    if (total == 0) return 0;
    int lastPage = (int) Math.min((total - 1) / size, Integer.MAX_VALUE);
    return Math.min(Math.max(requestedPage, 0), lastPage);
  }

  public String reveal(boolean hospital, long id) {
    if (hospital) owner(id);
    else departmentAdmin(id);
    String table = hospital ? "hospitals" : "departments";
    String code = jdbc.queryForObject("select join_code from " + table + " where id=?", String.class, id);
    audit.log("JOIN_CODE_VIEWED", hospital ? "Hospital" : "Department", id, "UI");
    return code;
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
    if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=? and owner=true", Boolean.class, hospitalId, actor.user().getId())))
      throw new ApiException(403, "HOSPITAL_OWNER_REQUIRED", "Only a hospital owner can manage this hospital.");
  }

  @Transactional
  public Map<String, Object> createHospital(String hospitalName, String departmentName) {
    String hospital = name(hospitalName);
    if (Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from hospitals where lower(name)=lower(?)", Boolean.class, hospital)))
      throw ApiException.conflict("HOSPITAL_NAME_TAKEN", "A hospital with this name already exists.");
    long id = jdbc.queryForObject("insert into hospitals(name,join_code) values (?,?) returning id", Long.class, hospital, code("H-"));
    jdbc.update("insert into hospital_memberships(hospital_id,user_id,owner) values (?,?,true)", id, actor.user().getId());
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
    jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'ADMIN')", id, actor.user().getId());
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
    guardJoinAttempts(user.getId(), remoteAddr);
    var departments = jdbc.queryForList("select id,hospital_id,join_code_expires_at,join_code_single_use from departments where join_code=?", code);
    if (!departments.isEmpty()) {
      var row = departments.getFirst();
      assertJoinFresh(row.get("join_code_expires_at"));
      long id = ((Number) row.get("id")).longValue();
      long hospitalId = ((Number) row.get("hospital_id")).longValue();
      if (Boolean.TRUE.equals(row.get("join_code_single_use"))) consumeJoinCode(false, id, code);
      clearJoinAttempts(user.getId(), remoteAddr);
      jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", hospitalId, user.getId());
      jdbc.update("insert into department_memberships(department_id,user_id,role) values (?,?,'MEDICAL_STAFF') on conflict do nothing", id, user.getId());
      audit.log("WORKSPACE_JOINED", "Department", id, "UI");
      return Map.of("hospitalId", hospitalId, "departmentId", id);
    }
    var hospitals = jdbc.queryForList("select id,join_code_expires_at,join_code_single_use from hospitals where join_code=?", code);
    if (hospitals.isEmpty()) {
      recordJoinFailure(user.getId(), remoteAddr);
      audit.log("JOIN_CODE_REJECTED", "Workspace", user.getId(), "UI");
      throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
    }
    var hospital = hospitals.getFirst();
    assertJoinFresh(hospital.get("join_code_expires_at"));
    long id = ((Number) hospital.get("id")).longValue();
    if (Boolean.TRUE.equals(hospital.get("join_code_single_use"))) consumeJoinCode(true, id, code);
    clearJoinAttempts(user.getId(), remoteAddr);
    jdbc.update("insert into hospital_memberships(hospital_id,user_id) values (?,?) on conflict do nothing", id, user.getId());
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

  private void assertJoinFresh(Object expires) {
    if (expires == null) return;
    Instant at = expires instanceof java.sql.Timestamp t ? t.toInstant() : Instant.parse(String.valueOf(expires));
    if (!at.isAfter(Instant.now()))
      throw new ApiException(400, "CODE_EXPIRED", "This join code has expired. Ask the owner for a new one.");
  }

  private void consumeJoinCode(boolean hospital, long id, String consumedCode) {
    String next = code(hospital ? "H-" : "D-");
    int consumed =
        jdbc.update(
            "update " + (hospital ? "hospitals" : "departments") + " set join_code=?, join_code_single_use=false, join_code_expires_at=null where id=? and join_code=? and join_code_single_use=true",
            next, id, consumedCode);
    if (consumed != 1)
      throw new ApiException(400, "INVALID_CODE", "This code is invalid. Check it with your hospital or department owner.");
  }

  @Transactional
  public String rotate(boolean hospital, long id) {
    return rotate(hospital, id, null, false);
  }

  @Transactional
  public String rotate(boolean hospital, long id, Integer expiresInHours, boolean singleUse) {
    if (hospital) owner(id);
    else if (!Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from department_memberships where department_id=? and user_id=? and role='ADMIN'", Boolean.class, id, actor.user().getId())))
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
        java.sql.Timestamp expires = expiresInHours == null || expiresInHours <= 0
            ? null
            : java.sql.Timestamp.from(Instant.now().plusSeconds(expiresInHours.longValue() * 3600L));
        jdbc.update("update " + table + " set join_code_expires_at=?, join_code_single_use=? where id=?", expires, singleUse, id);
        audit.log("JOIN_CODE_ROTATED", hospital ? "Hospital" : "Department", id, "UI");
        return next;
      } catch (DataIntegrityViolationException e) {
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
        Boolean.class, departmentId, actor.user().getId()))) return;
    Long hospitalId = jdbc.queryForObject("select hospital_id from departments where id=?", Long.class, departmentId);
    if (hospitalId == null) throw new ApiException(404, "NOT_FOUND", "Department not found.");
    owner(hospitalId);
  }

  @Transactional
  public Map<String, Object> setTimeZone(long departmentId, String supplied) {
    departmentAdmin(departmentId);
    String timeZone = supplied == null ? "" : supplied.strip();
    if (timeZone.isEmpty() || timeZone.length() > 64
        || !ZoneId.getAvailableZoneIds().contains(timeZone))
      throw new ApiException(400, "INVALID_TIME_ZONE", "Choose a valid IANA time zone.");
    jdbc.update("update departments set time_zone=? where id=?", timeZone, departmentId);
    audit.log("DEPARTMENT_TIME_ZONE_UPDATED", "Department", departmentId, "UI");
    return Map.of("timeZone", timeZone);
  }

  @Transactional
  public void leaveDepartment(long departmentId) {
    long userId = actor.user().getId();
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from department_memberships where department_id=? and user_id=?", Boolean.class, departmentId, userId)))
      throw new ApiException(404, "NOT_FOUND", "You are not a member of this department.");
    jdbc.update("delete from department_memberships where department_id=? and user_id=?", departmentId, userId);
    audit.log("DEPARTMENT_LEFT", "Department", departmentId, "UI");
  }

  @Transactional
  public void leaveHospital(long hospitalId) {
    long userId = actor.user().getId();
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
    if (userId == actor.user().getId()) {
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

  @Transactional
  public Map<String, Object> grantRole(long departmentId, long userId, String role, Long doctorId) {
    departmentAdmin(departmentId);
    String assigned = role == null ? "" : role.strip().toUpperCase(Locale.ROOT);
    if (!Set.of("ADMIN", "MEDICAL_STAFF", "DOCTOR").contains(assigned))
      throw new ApiException(400, "INVALID_ROLE", "Role must be administrator, medical staff, or doctor.");
    Long hospitalId = jdbc.queryForObject("select hospital_id from departments where id=?", Long.class, departmentId);
    if (hospitalId == null) throw ApiException.missing();
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*) > 0 from hospital_memberships where hospital_id=? and user_id=?",
        Boolean.class, hospitalId, userId)))
      throw new ApiException(400, "NOT_A_MEMBER", "That account must join the hospital first.");
    Long linked = "DOCTOR".equals(assigned) ? doctorInDepartment(departmentId, userId, doctorId) : null;
    jdbc.update(
        "insert into department_memberships(department_id,user_id,role,doctor_id) values (?,?,?,?) on conflict (department_id,user_id) do update set role=excluded.role, doctor_id=excluded.doctor_id",
        departmentId, userId, assigned, linked);
    audit.log("DEPARTMENT_ROLE_GRANTED", "Department", departmentId, "UI");
    var result = new LinkedHashMap<String, Object>();
    result.put("departmentId", departmentId);
    result.put("userId", userId);
    result.put("role", assigned);
    result.put("doctorId", linked);
    return result;
  }

  private long doctorInDepartment(long departmentId, long userId, Long doctorId) {
    if (doctorId != null) {
      if (!Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*) > 0 from doctors where id=? and department_id=? and active=true",
          Boolean.class, doctorId, departmentId)))
        throw new ApiException(400, "DOCTOR_REQUIRED", "Link an active doctor in this department.");
      return doctorId;
    }
    var source = jdbc.queryForList(
        "select d.first_name, d.last_name, d.specialty, d.doctor_identifier from doctors d join app_users u on u.doctor_id=d.id where u.id=?",
        userId);
    String first = "Clinician";
    String last = "User";
    String specialty = "General medicine";
    String identifier = "DOC-" + userId + "-" + departmentId;
    if (!source.isEmpty()) {
      var row = source.getFirst();
      first = String.valueOf(row.get("first_name"));
      last = String.valueOf(row.get("last_name"));
      specialty = String.valueOf(row.get("specialty"));
      identifier = String.valueOf(row.get("doctor_identifier")) + "-" + departmentId;
    } else {
      String username = jdbc.queryForObject("select username from app_users where id=?", String.class, userId);
      if (username != null && !username.isBlank()) last = username;
    }
    return jdbc.queryForObject(
        "insert into doctors(doctor_identifier, first_name, last_name, specialty, active, department_id, version, created_at, updated_at) values (?,?,?,?,true,?,0,now(),now()) returning id",
        Long.class, identifier, first, last, specialty, departmentId);
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
