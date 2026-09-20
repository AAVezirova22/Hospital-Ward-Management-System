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
  public boolean configured() { return !key.isBlank() && !from.isBlank() && !publicUrl.isBlank(); }
  public void send(String email, String firstName, String token, boolean doctor, int expiryMinutes) {
    if (!configured()) throw new ApiException(503,"EMAIL_UNAVAILABLE","Email confirmation is not configured yet.");
    try {
      URI base = URI.create(publicUrl);
      if (!Set.of("https", "http").contains(base.getScheme()) || base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null)
        throw new IllegalStateException("Invalid public URL configuration");
      String url = publicUrl + "/app/dashboard#verify=" + token;
      String detail = doctor ? "After email confirmation, an administrator will review your doctor access request. You can use your patient account while the request is pending." : "Your patient workspace keeps your own admissions and care history together.";
      String html;
      try (var stream = new ClassPathResource("emails/confirm-account.html").getInputStream()) {
        html = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
            .replace("{{firstName}}",escape(firstName)).replace("{{confirmationUrl}}",escape(url))
            .replace("{{accountDetail}}",escape(detail)).replace("{{expiryMinutes}}",String.valueOf(expiryMinutes));
      }
      var payload = Map.of("from",from,"to",List.of(email),"subject","Confirm your Medcore account",
          "html",html,"text","Hello " + firstName + ",\n\nConfirm your email: " + url + "\n\n" + detail + "\nThis link expires in " + expiryMinutes + " minutes. If you did not request an account, ignore this email.");
      var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(15))
          .header("Authorization","Bearer " + key).header("Content-Type","application/json")
          .header("Idempotency-Key","verification-" + token)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
      var response = client.send(request,HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ApiException(503,"EMAIL_DELIVERY_FAILED","The email provider could not deliver the confirmation. Please try again later or contact the administrator.");
    } catch (ApiException e) { throw e; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(503,"EMAIL_UNAVAILABLE","Email delivery was interrupted. Please retry."); }
    catch (Exception e) { throw new ApiException(503,"EMAIL_UNAVAILABLE","Email confirmation is temporarily unavailable."); }
  }
  static String escape(String value) { return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
}
