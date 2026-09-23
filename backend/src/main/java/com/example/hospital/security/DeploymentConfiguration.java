package com.example.hospital.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

/**
 * Fails startup on missing or incompatible deployment settings. Every problem is reported at once
 * by environment-variable name; configured values are never included because some are secrets.
 */
@Component
public class DeploymentConfiguration {
  private static final Set<String> AI_MODES = Set.of("local", "external", "off");

  public DeploymentConfiguration(PropertyResolver env) {
    var problems = problems(env);
    if (!problems.isEmpty())
      throw new IllegalStateException(
          "Invalid deployment configuration:\n  - " + String.join("\n  - ", problems));
  }

  public static List<String> problems(PropertyResolver env) {
    var problems = new ArrayList<String>();

    String password = value(env, "spring.datasource.password");
    if (password.isBlank() || "hospital-local-only".equals(password))
      problems.add("DATABASE_PASSWORD must be set; the committed local fallback is not allowed.");
    if (!value(env, "spring.datasource.url").startsWith("jdbc:postgresql:"))
      problems.add("DATABASE_URL must be a JDBC PostgreSQL URL (jdbc:postgresql://host:port/db).");

    String bootstrap = value(env, "app.bootstrap-password");
    if (!bootstrap.isEmpty()
        && (bootstrap.length() < 12 || bootstrap.getBytes(StandardCharsets.UTF_8).length > 72))
      problems.add("BOOTSTRAP_PASSWORD must be 12 characters to 72 UTF-8 bytes.");

    String publicUrl = value(env, "app.public-url");
    boolean httpsOrigin = false;
    if (!publicUrl.isBlank()) {
      URI uri = uri(publicUrl);
      boolean origin =
          uri != null
              && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
              && uri.getHost() != null
              && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
              && uri.getQuery() == null
              && uri.getFragment() == null;
      if (!origin)
        problems.add("PUBLIC_APP_URL must be an http(s) origin without a path, query or fragment.");
      httpsOrigin = origin && "https".equals(uri.getScheme());
    }
    if (httpsOrigin && !Boolean.parseBoolean(value(env, "server.servlet.session.cookie.secure")))
      problems.add("COOKIE_SECURE must be true when PUBLIC_APP_URL uses https.");

    boolean emailKey = !value(env, "app.email.resend-key").isBlank();
    boolean emailFrom = !value(env, "app.email.from").isBlank();
    // A sender without a key simply leaves email disabled; a key without a sender cannot send.
    if (emailKey && !emailFrom)
      problems.add("EMAIL_FROM is required when RESEND_API_KEY is set.");
    if (emailKey && publicUrl.isBlank())
      problems.add("PUBLIC_APP_URL is required when RESEND_API_KEY is set (used in email links).");
    String expiry = value(env, "app.registration.expiry-minutes");
    if (!expiry.isBlank() && !between(expiry, 5, 1440))
      problems.add("EMAIL_CONFIRMATION_MINUTES must be a whole number from 5 to 1440.");

    String mode = value(env, "app.ai.mode");
    if (!AI_MODES.contains(mode)) problems.add("AI_MODE must be one of local, external or off.");
    if ("external".equals(mode)) {
      URI ai = uri(value(env, "app.ai.url"));
      if (ai == null || !("https".equals(ai.getScheme()) || "http".equals(ai.getScheme())) || ai.getHost() == null)
        problems.add("AI_URL must be an http(s) URL when AI_MODE=external.");
      if (value(env, "app.ai.model").isBlank())
        problems.add("AI_MODEL is required when AI_MODE=external.");
    }
    return problems;
  }

  private static String value(PropertyResolver env, String key) {
    String v = env.getProperty(key);
    return v == null ? "" : v.trim();
  }

  private static URI uri(String text) {
    try {
      return text.isBlank() ? null : new URI(text);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean between(String text, int min, int max) {
    try {
      int n = Integer.parseInt(text);
      return n >= min && n <= max;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
