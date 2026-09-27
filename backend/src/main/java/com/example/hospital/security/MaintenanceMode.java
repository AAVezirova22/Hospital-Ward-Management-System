package com.example.hospital.security;

import com.example.hospital.api.Errors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Planned maintenance switch (#421). While {@code MAINTENANCE_MODE=true}, API requests get a clear
 * {@code 503 MAINTENANCE} answer with a retry hint instead of ordinary errors from a half-migrated
 * database. Health probes and the maintenance status stay reachable. Operators who send
 * {@code X-Maintenance-Bypass} with the configured token can use the API to verify the release.
 */
@Component
public class MaintenanceMode {
  public static final String BYPASS_HEADER = "X-Maintenance-Bypass";
  private static final Set<String> ALWAYS_OPEN = Set.of(
      "/api/v1/health", "/api/v1/health/live", "/api/v1/health/ready", "/api/v1/maintenance");

  private final boolean enabled;
  private final String message;
  private final int retryAfterSeconds;
  private final byte[] bypassHash;

  public MaintenanceMode(
      @Value("${app.maintenance.enabled:false}") boolean enabled,
      @Value("${app.maintenance.message:}") String message,
      @Value("${app.maintenance.retry-after-seconds:120}") int retryAfterSeconds,
      @Value("${app.maintenance.bypass-token:}") String bypassToken) {
    if (retryAfterSeconds < 5 || retryAfterSeconds > 86400)
      throw new IllegalStateException("MAINTENANCE_RETRY_AFTER_SECONDS must be between 5 and 86400.");
    String token = bypassToken == null ? "" : bypassToken.strip();
    if (!token.isEmpty() && token.length() < 24)
      throw new IllegalStateException("MAINTENANCE_BYPASS_TOKEN must be at least 24 characters when set.");
    this.enabled = enabled;
    this.message = message == null || message.isBlank()
        ? "Scheduled maintenance is in progress. Please try again shortly."
        : message.strip();
    this.retryAfterSeconds = retryAfterSeconds;
    this.bypassHash = token.isEmpty() ? null : sha256(token);
  }

  public Map<String, Object> status() {
    var status = new LinkedHashMap<String, Object>();
    status.put("active", enabled);
    if (enabled) {
      status.put("message", message);
      status.put("retryAfterSeconds", retryAfterSeconds);
    }
    return status;
  }

  boolean blocks(HttpServletRequest request) {
    if (!enabled) return false;
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith("/api/") || ALWAYS_OPEN.contains(path)) return false;
    String supplied = request.getHeader(BYPASS_HEADER);
    return bypassHash == null || supplied == null || !MessageDigest.isEqual(bypassHash, sha256(supplied.strip()));
  }

  public OncePerRequestFilter filter(ObjectMapper json) {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        if (!blocks(request)) {
          chain.doFilter(request, response);
          return;
        }
        response.setStatus(503);
        response.setHeader("Retry-After", Integer.toString(retryAfterSeconds));
        response.setContentType("application/json");
        var body = new LinkedHashMap<String, Object>(Errors.body(503, "MAINTENANCE", message, request.getRequestURI()));
        body.put("retryAfterSeconds", retryAfterSeconds);
        json.writeValue(response.getWriter(), body);
      }
    };
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
