package com.example.hospital.security;

import com.example.hospital.api.Errors;
import com.example.hospital.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes selected create and workflow endpoints safe to retry (#387). A request carrying an
 * {@code Idempotency-Key} is run once per account and key; retries with the same body receive the
 * stored response (marked {@code Idempotent-Replayed: true}), a different body gets 422, and a
 * retry while the first attempt is still running gets 409. Server errors are not stored, so they
 * can be retried. Stored responses expire after {@code IDEMPOTENCY_KEY_TTL}.
 */
public class IdempotencyFilter extends OncePerRequestFilter {
  public static final String HEADER = "Idempotency-Key";
  static final List<Pattern> PATHS =
      List.of(
          Pattern.compile("/api/v1/patients"),
          Pattern.compile("/api/v1/admissions"),
          Pattern.compile("/api/v1/admissions/\\d+/(transfer|discharge|doctor|procedures)"),
          Pattern.compile("/api/v1/ai-actions/\\d+/confirm"),
          Pattern.compile("/api/v1/rooms/\\d+/holds"));
  private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final int MAX_STORED_BODY = 64 * 1024;

  private final JdbcTemplate jdbc;
  private final AppUserRepository users;
  private final ObjectMapper json;
  private final Duration ttl;

  public IdempotencyFilter(JdbcTemplate jdbc, AppUserRepository users, ObjectMapper json, Duration ttl) {
    this.jdbc = jdbc;
    this.users = users;
    this.json = json;
    this.ttl = ttl;
  }

  static boolean covered(String method, String path) {
    return "POST".equals(method) && PATHS.stream().anyMatch(p -> p.matcher(path).matches());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER);
    String path = request.getRequestURI().substring(request.getContextPath().length());
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (key == null || !covered(request.getMethod(), path) || authentication == null) {
      chain.doFilter(request, response);
      return;
    }
    if (!KEY.matcher(key).matches()) {
      error(request, response, 400, "INVALID_IDEMPOTENCY_KEY", "Use 1 to 128 letters, digits or . _ : - in Idempotency-Key.");
      return;
    }
    var user = users.findByUsername(authentication.getName());
    if (user.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    long userId = user.get().getId();
    byte[] body = request.getInputStream().readAllBytes();
    String hash = sha256(request.getMethod() + " " + path + "\n" + DepartmentContext.id() + "\n", body);
    jdbc.update("delete from idempotency_keys where created_at < ?", Timestamp.from(Instant.now().minus(ttl)));
    try {
      jdbc.update(
          "insert into idempotency_keys(user_id, idempotency_key, request_hash, status) values (?,?,?,'IN_PROGRESS')",
          userId, key, hash);
    } catch (DuplicateKeyException e) {
      replay(request, response, userId, key, hash);
      return;
    }
    var cached = new ContentCachingResponseWrapper(response);
    boolean stored = false;
    try {
      chain.doFilter(new CachedBodyRequest(request, body), cached);
      int status = cached.getStatus();
      byte[] content = cached.getContentAsByteArray();
      if (status < 500 && content.length <= MAX_STORED_BODY) {
        jdbc.update(
            "update idempotency_keys set status='COMPLETED', response_status=?, response_content_type=?, response_body=?"
                + " where user_id=? and idempotency_key=?",
            status, cached.getContentType(), new String(content, StandardCharsets.UTF_8), userId, key);
        stored = true;
      }
    } finally {
      if (!stored) jdbc.update("delete from idempotency_keys where user_id=? and idempotency_key=?", userId, key);
      cached.copyBodyToResponse();
    }
  }

  private void replay(HttpServletRequest request, HttpServletResponse response, long userId, String key, String hash)
      throws IOException {
    var rows = jdbc.queryForList(
        "select request_hash, status, response_status, response_content_type, response_body from idempotency_keys"
            + " where user_id=? and idempotency_key=?",
        userId, key);
    if (rows.isEmpty()) {
      error(request, response, 409, "IDEMPOTENCY_IN_PROGRESS", "The first request with this key is still running. Retry shortly.");
      return;
    }
    Map<String, Object> row = rows.getFirst();
    if (!hash.equals(row.get("request_hash"))) {
      error(request, response, 422, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different request.");
      return;
    }
    if (!"COMPLETED".equals(row.get("status"))) {
      error(request, response, 409, "IDEMPOTENCY_IN_PROGRESS", "The first request with this key is still running. Retry shortly.");
      return;
    }
    response.setStatus(((Number) row.get("response_status")).intValue());
    response.setHeader("Idempotent-Replayed", "true");
    if (row.get("response_content_type") != null) response.setContentType(String.valueOf(row.get("response_content_type")));
    if (row.get("response_body") != null)
      response.getOutputStream().write(String.valueOf(row.get("response_body")).getBytes(StandardCharsets.UTF_8));
  }

  private void error(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    json.writeValue(response.getWriter(), Errors.body(status, code, message, request.getRequestURI()));
  }

  private static String sha256(String prefix, byte[] body) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(prefix.getBytes(StandardCharsets.UTF_8));
      digest.update(body);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /** Lets the controller read a body that was already consumed for hashing. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      var input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        public boolean isFinished() { return input.available() == 0; }
        public boolean isReady() { return true; }
        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
        public int read() { return input.read(); }
        public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
