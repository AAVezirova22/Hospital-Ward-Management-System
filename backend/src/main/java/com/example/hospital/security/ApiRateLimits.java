package com.example.hospital.security;

import com.example.hospital.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bounded request budgets for expensive or abuse-prone endpoints (#355), shared across instances
 * through {@link RateLimitService}. Sign-in and registration are counted per client address;
 * searches, reports and exports per signed-in account. Keys are hashed, so the limit table never
 * stores usernames or addresses.
 */
@Component
public class ApiRateLimits {
  public record Policy(String name, int limit, Duration window, boolean perAccount) {}

  private static final Set<String> SEARCH_PATHS =
      Set.of(
          "/api/v1/patients",
          "/api/v1/admissions",
          "/api/v1/doctors",
          "/api/v1/rooms",
          "/api/v1/procedures",
          "/api/v1/audit");

  private final boolean enabled;
  private final Map<String, Policy> policies;
  private final RateLimitService rates;
  private final ClientAddressResolver clients;

  public ApiRateLimits(
      @Value("${app.rate-limits.enabled:true}") boolean enabled,
      @Value("${app.rate-limits.auth:60/1m}") String auth,
      @Value("${app.rate-limits.registration:30/10m}") String registration,
      @Value("${app.rate-limits.search:300/1m}") String search,
      @Value("${app.rate-limits.reports:120/1m}") String reports,
      @Value("${app.rate-limits.exports:20/10m}") String exports,
      RateLimitService rates,
      ClientAddressResolver clients) {
    this.enabled = enabled;
    this.rates = rates;
    this.clients = clients;
    var configured = new LinkedHashMap<String, Policy>();
    put(configured, parse("auth", auth, false, "API_RATE_LIMIT_AUTH"));
    put(configured, parse("registration", registration, false, "API_RATE_LIMIT_REGISTRATION"));
    put(configured, parse("search", search, true, "API_RATE_LIMIT_SEARCH"));
    put(configured, parse("reports", reports, true, "API_RATE_LIMIT_REPORTS"));
    put(configured, parse("exports", exports, true, "API_RATE_LIMIT_EXPORTS"));
    this.policies = Map.copyOf(configured);
  }

  /** Parses {@code count/duration} such as {@code 60/1m}, or {@code off}. Returns null when off. */
  static Policy parse(String name, String value, boolean perAccount, String variable) {
    String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    if (text.equals("off")) return null;
    int slash = text.indexOf('/');
    try {
      if (slash < 1) throw new IllegalArgumentException();
      int limit = Integer.parseInt(text.substring(0, slash));
      Duration window = duration(text.substring(slash + 1));
      if (limit < 1 || window.isZero() || window.isNegative() || window.compareTo(RateLimitService.MAX_WINDOW) > 0)
        throw new IllegalArgumentException();
      return new Policy(name, limit, window, perAccount);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          variable + " must look like 60/1m (count per window of up to 1d) or be 'off'.", e);
    }
  }

  private static Duration duration(String text) {
    if (text.matches("\\d+[smhd]")) {
      long amount = Long.parseLong(text.substring(0, text.length() - 1));
      return switch (text.charAt(text.length() - 1)) {
        case 's' -> Duration.ofSeconds(amount);
        case 'm' -> Duration.ofMinutes(amount);
        case 'h' -> Duration.ofHours(amount);
        default -> Duration.ofDays(amount);
      };
    }
    try {
      return Duration.parse(text.toUpperCase(Locale.ROOT));
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static void put(Map<String, Policy> policies, Policy policy) {
    if (policy != null) policies.put(policy.name(), policy);
  }

  public Policy policy(String name) {
    return policies.get(name);
  }

  /** The policy that governs a request, or null when it is not limited. */
  String policyFor(HttpServletRequest request) {
    String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
    String context = request.getContextPath() == null ? "" : request.getContextPath();
    String path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
    String method = request.getMethod();
    if ("POST".equals(method)) {
      if ("/api/v1/auth/login".equals(path)) return "auth";
      if (path.startsWith("/api/v1/registration/")) return "registration";
    }
    if ("GET".equals(method)) {
      if (path.startsWith("/api/v1/reports/")) return path.endsWith(".csv") ? "exports" : "reports";
      if (SEARCH_PATHS.contains(path)) return "search";
    }
    return null;
  }

  /** Counts the request against its policy; throws a 429 {@code ApiException} when spent. */
  public void check(HttpServletRequest request) {
    if (!enabled) return;
    String name = policyFor(request);
    Policy policy = name == null ? null : policies.get(name);
    if (policy == null) return;
    String account = account();
    String scope =
        policy.perAccount() && account != null ? "user:" + account : "ip:" + clients.sourceAddress(request);
    rates.hit(
        "api:" + policy.name() + ":" + sha256(scope),
        policy.limit(),
        policy.window(),
        "RATE_LIMITED",
        "Too many requests. Wait a moment before trying again.");
  }

  private static String account() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) return null;
    return authentication.getName().toLowerCase(Locale.ROOT);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
