package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ConfirmationEmailService {
  private final String key, from, publicUrl, endpoint;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  public ConfirmationEmailService(@Value("${app.email.resend-key:}") String key,
      @Value("${app.email.from:}") String from, @Value("${app.public-url:}") String publicUrl,
      @Value("${app.email.endpoint:https://api.resend.com/emails}") String endpoint, ObjectMapper json) {
    this.key=key; this.from=from; this.publicUrl=publicUrl.replaceAll("/+$", ""); this.endpoint=endpoint; this.json=json;
  }
  public boolean configured() { return remindersConfigured() && !publicUrl.isBlank(); }
  public boolean remindersConfigured() { return !key.isBlank() && !from.isBlank(); }
  public void send(String email, String firstName, String token, boolean doctor, int expiryMinutes) {
    if (!configured()) throw new ApiException(503,"EMAIL_UNAVAILABLE","Email confirmation is not configured yet.");
    try {
      URI base = URI.create(publicUrl);
      if (!Set.of("https", "http").contains(base.getScheme()) || base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null)
        throw new IllegalStateException("Invalid public URL configuration");
      String url = publicUrl + "/verify?token=" + token;
      String detail = doctor ? "After email confirmation, an administrator will review your doctor access request. You can use your patient account while the request is pending." : "Your patient workspace keeps your own admissions and care history together.";
      String html;
      try (var stream = new ClassPathResource("emails/confirm-account.html").getInputStream()) {
        html = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
            .replace("{{firstName}}",escape(firstName)).replace("{{confirmationUrl}}",escape(url))
            .replace("{{accountDetail}}",escape(detail)).replace("{{expiryMinutes}}",String.valueOf(expiryMinutes));
      }
      var payload = Map.of("from",from,"to",List.of(email),"subject","Confirm your Medcore account",
          "html",html,"text","Hello " + firstName + ",\n\nConfirm your email: " + url + "\n\n" + detail + "\nThis link expires in " + expiryMinutes + " minutes. If you did not request an account, ignore this email.");
      deliver(payload, "verification-" + token);
    } catch (ApiException e) { throw e; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(503,"EMAIL_UNAVAILABLE","Email delivery was interrupted. Please retry."); }
    catch (Exception e) { throw new ApiException(503,"EMAIL_UNAVAILABLE","Email confirmation is temporarily unavailable."); }
  }

  public DeliveryResult sendReminder(List<String> recipients, String subject, String text, String idempotencyKey) {
    if (!remindersConfigured()) throw new ApiException(503, "EMAIL_UNAVAILABLE", "Email delivery is not configured yet.");
    if (recipients.isEmpty()) throw new IllegalArgumentException("At least one verified recipient is required.");
    var payload = Map.of("from", from, "to", List.copyOf(recipients), "subject", subject, "text", text);
    try {
      return deliver(payload, idempotencyKey);
    } catch (ApiException e) { throw e; }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "EMAIL_UNAVAILABLE", "Email delivery was interrupted. Please retry.");
    } catch (Exception e) {
      throw new ApiException(503, "EMAIL_UNAVAILABLE", "Email delivery is temporarily unavailable.");
    }
  }

  private HttpRequest providerRequest(Map<String, ?> payload, String idempotencyKey) throws Exception {
    return HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(15))
        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
        .header("Idempotency-Key", idempotencyKey)
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
  }

  private DeliveryResult deliver(Map<String, ?> payload, String idempotencyKey) throws Exception {
    var response = client.send(providerRequest(payload, idempotencyKey), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      throw new ApiException(503, "EMAIL_DELIVERY_FAILED", "The email provider could not accept this message.");
    String providerMessageId = null;
    if (response.body() != null && !response.body().isBlank())
      providerMessageId = json.readTree(response.body()).path("id").asText(null);
    return new DeliveryResult(providerMessageId);
  }

  public record DeliveryResult(String providerMessageId) {}

  /** Outcome of an administrator test send; carries no provider response text or credentials. */
  public record TestResult(String outcome, Integer providerStatus, String providerMessageId) {}

  private static final java.util.regex.Pattern SENDER = java.util.regex.Pattern.compile(
      "[^<>@\\s]+@[^<>@\\s]+\\.[^<>@\\s]+|[^<>@]*[^<>@\\s][^<>@]*<[^<>@\\s]+@[^<>@\\s]+\\.[^<>@\\s]+>");

  public boolean keyPresent() { return !key.isBlank(); }
  public String sender() { return from; }
  public String publicUrl() { return publicUrl; }
  public String endpointHost() {
    try { return URI.create(endpoint).getHost(); } catch (IllegalArgumentException e) { return null; }
  }
  public boolean sendTestSenderValid() { return validSender(from); }
  static boolean validSender(String value) { return value != null && SENDER.matcher(value.strip()).matches(); }

  /** Sends a short, content-free test message and classifies the provider response. */
  public TestResult sendTest(String recipient) {
    if (!remindersConfigured()) return new TestResult("NOT_CONFIGURED", null, null);
    if (!validSender(from)) return new TestResult("INVALID_SENDER", null, null);
    var payload = Map.of("from", from, "to", List.of(recipient), "subject", "Medcore email settings test",
        "text", "An administrator sent this message to check Medcore email delivery settings. No action is needed.");
    try {
      var response = client.send(providerRequest(payload, "settings-test-" + UUID.randomUUID()), HttpResponse.BodyHandlers.ofString());
      int code = response.statusCode();
      if (code >= 200 && code < 300) {
        String id = response.body() == null || response.body().isBlank() ? null : json.readTree(response.body()).path("id").asText(null);
        return new TestResult("ACCEPTED_BY_PROVIDER", code, id);
      }
      return new TestResult(code >= 500 || code == 429 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_REJECTED", code, null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new TestResult("NETWORK_ERROR", null, null);
    } catch (Exception e) {
      return new TestResult("NETWORK_ERROR", null, null);
    }
  }
  static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
}
