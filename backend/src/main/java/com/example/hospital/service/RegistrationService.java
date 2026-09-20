package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
  private final AppUserRepository users;
  private final PatientRepository patients;
  private final PasswordEncoder encoder;
  private final ConfirmationEmailService email;
  private final JdbcTemplate jdbc;
  private final WorkflowLockRepository lock;
  private final boolean enabled;
  private final int expiryMinutes;
  private final Map<String, Instant> attempts = new ConcurrentHashMap<>();
  public RegistrationService(AppUserRepository users, PatientRepository patients, PasswordEncoder encoder,
      ConfirmationEmailService email, JdbcTemplate jdbc, WorkflowLockRepository lock,
      @Value("${app.registration.enabled:true}") boolean enabled,
      @Value("${app.registration.expiry-minutes:30}") int expiryMinutes) {
    this.users=users; this.patients=patients; this.encoder=encoder; this.email=email;
    this.jdbc=jdbc; this.lock=lock; this.enabled=enabled; this.expiryMinutes=expiryMinutes;
  }
  public boolean available() { return enabled && email.configured(); }
  public synchronized void limit(String address) {
    Instant now = Instant.now(); attempts.entrySet().removeIf(e -> e.getValue().isBefore(now.minusSeconds(60)));
    if (attempts.containsKey(address) || attempts.size() >= 10000)
      throw new ApiException(429,"RATE_LIMITED","Please wait a minute before requesting another confirmation email.");
    attempts.put(address,now);
  }
  @Transactional
  public void signup(String username, String address, String password, String first, String last, LocalDate dob, String requestedRole) {
    if (!available()) throw new ApiException(503,"REGISTRATION_UNAVAILABLE","Account registration is not configured yet.");
    if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400,"PASSWORD_LENGTH","Use at least 12 characters and at most 72 UTF-8 bytes.");
    com.example.hospital.security.DepartmentContext.set(
        new com.example.hospital.security.DepartmentContext.Scope(1L, "PATIENT", null));
    try {
    lock.acquire();
    String normalized = address.trim().toLowerCase(Locale.ROOT);
    if (users.findByUsername(username).isPresent() || Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from app_users where email = ?",Boolean.class,normalized)))
      throw ApiException.conflict("ACCOUNT_EXISTS","An account with these details already exists. Sign in or request a new confirmation.");
    var patient = new Patient(); patient.firstName=first.trim(); patient.lastName=last.trim(); patient.dateOfBirth=dob;
    patient.patientIdentifier="SELF-" + UUID.randomUUID(); patients.saveAndFlush(patient);
    var u = new AppUser(); u.username=username; u.email=normalized; u.passwordHash=encoder.encode(password);
    u.role="PATIENT"; u.requestedRole=requestedRole; u.enabled=false; u.patientId=patient.id; users.saveAndFlush(u);
    issue(u,first);
    } finally {
      com.example.hospital.security.DepartmentContext.clear();
    }
  }
  private void issue(AppUser user,String first) {
    byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    jdbc.update("delete from email_verifications where user_id = ?",user.id);
    jdbc.update("insert into email_verifications(token_hash,user_id,expires_at) values (?,?,?)",hash(token),user.id,Timestamp.from(Instant.now().plusSeconds(expiryMinutes*60L)));
    email.send(user.email,first,token,"DOCTOR".equals(user.requestedRole),expiryMinutes);
  }
  @Transactional
  public void resend(String address) {
    if (!available()) throw new ApiException(503,"REGISTRATION_UNAVAILABLE","Account registration is not configured yet.");
    lock.acquire();
    var ids=jdbc.queryForList("select id from app_users where email=? and email_verified=false",Long.class,address.trim().toLowerCase(Locale.ROOT));
    if (!ids.isEmpty()) {var u=users.findById(ids.getFirst()).orElseThrow();issue(u,patients.findById(u.patientId).orElseThrow().firstName);}
  }
  @Transactional
  public void verify(String token) {
    if (!enabled) throw new ApiException(404,"REGISTRATION_UNAVAILABLE","Registration is disabled.");
    lock.acquire();
    var ids=jdbc.queryForList("select user_id from email_verifications where token_hash=? and used_at is null and expires_at>? for update",Long.class,hash(token),Timestamp.from(Instant.now()));
    if (ids.isEmpty()) throw new ApiException(400,"INVALID_CONFIRMATION","This confirmation link has expired or was already used. Request a new email.");
    var u=users.findById(ids.getFirst()).orElseThrow(); u.emailVerified=true; u.enabled=true; users.saveAndFlush(u);
    jdbc.update("update email_verifications set used_at=? where token_hash=?",Timestamp.from(Instant.now()),hash(token));
  }
  private static String hash(String token) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
    catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
  }
}
