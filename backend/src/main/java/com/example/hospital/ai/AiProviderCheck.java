package com.example.hospital.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Administrator connection check for the external AI provider (#333). It verifies the endpoint
 * protocol and that the provider returns exactly one function call for a synthetic tool, using a
 * fixed prompt that contains no hospital or patient data. Keys and provider text are never returned.
 */
@Component
public class AiProviderCheck {
  private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

  private final String mode;
  private final String url;
  private final String key;
  private final String model;
  private final int timeout;
  private final ObjectMapper json;

  public AiProviderCheck(
      @Value("${app.ai.mode}") String mode,
      @Value("${app.ai.url}") String url,
      @Value("${app.ai.key}") String key,
      @Value("${app.ai.model}") String model,
      @Value("${app.ai.timeout-seconds}") int timeout,
      ObjectMapper json) {
    this.mode = mode;
    this.url = url == null ? "" : url.strip();
    this.key = key == null ? "" : key;
    this.model = model == null ? "" : model.strip();
    this.timeout = timeout;
    this.json = json;
  }

  public Map<String, Object> configuration() {
    var result = new LinkedHashMap<String, Object>();
    result.put("mode", mode);
    result.put("providerHost", host());
    result.put("model", model.isEmpty() ? null : model);
    result.put("apiKeyConfigured", !key.isBlank());
    result.put("timeoutSeconds", timeout);
    return result;
  }

  public Map<String, Object> run() {
    var result = new LinkedHashMap<String, Object>(configuration());
    result.put("checkedAt", Instant.now());
    if (!"external".equals(mode)) return outcome(result, "NOT_EXTERNAL", null);
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return outcome(result, "INVALID_URL", null);
    }
    boolean https = "https".equals(uri.getScheme());
    boolean localHttp = "http".equals(uri.getScheme()) && uri.getHost() != null && LOOPBACK.contains(uri.getHost());
    if (uri.getHost() == null || !(https || localHttp)) return outcome(result, "INSECURE_PROTOCOL", null);
    long started = System.nanoTime();
    try {
      var body = Map.of(
          "model", model,
          "messages", List.of(
              Map.of("role", "system", "content", "Connection check. Call the connectionCheck tool with value \"ok\"."),
              Map.of("role", "user", "content", "Run the connection check.")),
          "tools", List.of(Map.of("type", "function", "function", Map.of(
              "name", "connectionCheck",
              "description", "Confirms that tool calls work.",
              "parameters", Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string")),
                  "required", List.of("value"), "additionalProperties", false)))),
          "tool_choice", "required",
          "parallel_tool_calls", false,
          "temperature", 0);
      var request = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(timeout))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      if (!key.isBlank()) request.header("Authorization", "Bearer " + key);
      var client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(timeout))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      result.put("latencyMs", (System.nanoTime() - started) / 1_000_000);
      if (response.statusCode() == 401 || response.statusCode() == 403)
        return outcome(result, "AUTHENTICATION_FAILED", response.statusCode());
      if (response.statusCode() != 200) return outcome(result, "PROVIDER_ERROR", response.statusCode());
      if (response.body() == null || response.body().length() > 64000)
        return outcome(result, "TOOL_CALLS_UNSUPPORTED", 200);
      var calls = json.readTree(response.body()).path("choices").path(0).path("message").path("tool_calls");
      if (!calls.isArray() || calls.size() != 1
          || !"connectionCheck".equals(calls.get(0).path("function").path("name").asText())
          || !json.readTree(calls.get(0).path("function").path("arguments").asText("{}")).isObject())
        return outcome(result, "TOOL_CALLS_UNSUPPORTED", 200);
      return outcome(result, "COMPATIBLE", 200);
    } catch (HttpTimeoutException e) {
      return outcome(result, "TIMEOUT", null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return outcome(result, "UNREACHABLE", null);
    } catch (Exception e) {
      return outcome(result, "UNREACHABLE", null);
    }
  }

  private static Map<String, Object> outcome(Map<String, Object> result, String outcome, Integer status) {
    result.put("outcome", outcome);
    result.put("providerStatus", status);
    return result;
  }

  private String host() {
    try {
      return url.isEmpty() ? null : URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
