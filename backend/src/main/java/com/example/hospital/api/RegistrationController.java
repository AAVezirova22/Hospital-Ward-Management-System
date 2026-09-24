package com.example.hospital.api;

import com.example.hospital.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registration")
public class RegistrationController {
  private final RegistrationService registration;
  private final com.example.hospital.security.ClientAddressResolver clientAddresses;
  public RegistrationController(RegistrationService registration, com.example.hospital.security.ClientAddressResolver clientAddresses) {
    this.registration=registration;
    this.clientAddresses=clientAddresses;
  }
  public record Signup(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,64}") String username,
      @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password,
      @NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName,
      @NotNull @Past LocalDate dateOfBirth, @NotNull @Pattern(regexp="PATIENT|DOCTOR") String requestedRole,
      @NotNull Long hospitalId) {}
  public record Verify(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
  public record Resend(@NotBlank @Email @Size(max=254) String email) {}
  public record Recover(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,64}") String username,
      @NotBlank @Size(min=12,max=72) String password, @NotBlank @Email @Size(max=254) String email) {}
  @GetMapping("/status") public Object status() {return Map.of("enabled",registration.available());}
  @GetMapping("/hospitals") public Object hospitals() {return registration.hospitals();}
  @PostMapping("/signup") public Object signup(@Valid @RequestBody Signup in,HttpServletRequest request) {
    registration.limit(clientKey(request, in.email()));
    registration.signup(in.username(),in.email(),in.password(),in.firstName(),in.lastName(),in.dateOfBirth(),in.requestedRole(),in.hospitalId());
    return Map.of("message","Check your inbox to confirm your email. Doctor requests require administrator approval after verification.");
  }
  @PostMapping("/verify") public Object verify(@Valid @RequestBody Verify in) {registration.verify(in.token());return Map.of("message","Email confirmed. You can now sign in. Doctor access requests will be reviewed by an administrator.");}
  @PostMapping("/resend") public Object resend(@Valid @RequestBody Resend in,HttpServletRequest request) {registration.limit(clientKey(request, in.email()));registration.resend(in.email());return Map.of("message","If an unverified account matches, a new confirmation email will be sent.");}
  @PostMapping("/recover") public Object recover(@Valid @RequestBody Recover in,HttpServletRequest request) {
    registration.limit(clientKey(request, in.email()));
    registration.limit(clientKey(request, "username:" + in.username()));
    registration.recover(in.username(),in.password(),in.email());
    return Map.of("message","If the registration can be recovered, a fresh confirmation email will be sent.");
  }

  private String clientKey(HttpServletRequest request, String identifier) {
    String client = clientAddresses.sourceAddress(request);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest((client + ":" + identifier.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
