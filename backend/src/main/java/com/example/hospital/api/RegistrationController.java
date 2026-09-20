package com.example.hospital.api;

import com.example.hospital.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registration")
public class RegistrationController {
  private final RegistrationService registration;
  public RegistrationController(RegistrationService registration) {this.registration=registration;}
  public record Signup(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,64}") String username,
      @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password,
      @NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName,
      @NotNull @Past LocalDate dateOfBirth, @NotNull @Pattern(regexp="PATIENT|DOCTOR") String requestedRole,
      @NotNull Long hospitalId) {}
  public record Verify(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
  public record Resend(@NotBlank @Email @Size(max=254) String email) {}
  @GetMapping("/status") public Object status() {return Map.of("enabled",registration.available());}
  @GetMapping("/hospitals") public Object hospitals() {return registration.hospitals();}
  @PostMapping("/signup") public Object signup(@Valid @RequestBody Signup in,HttpServletRequest request) {
    registration.limit(request.getRemoteAddr());
    registration.signup(in.username(),in.email(),in.password(),in.firstName(),in.lastName(),in.dateOfBirth(),in.requestedRole(),in.hospitalId());
    return Map.of("message","Check your inbox to confirm your email. Doctor requests require administrator approval after verification.");
  }
  @PostMapping("/verify") public Object verify(@Valid @RequestBody Verify in) {registration.verify(in.token());return Map.of("message","Email confirmed. You can now sign in. Doctor access requests will be reviewed by an administrator.");}
  @PostMapping("/resend") public Object resend(@Valid @RequestBody Resend in,HttpServletRequest request) {registration.limit(request.getRemoteAddr());registration.resend(in.email());return Map.of("message","If an unverified account matches, a new confirmation email has been sent.");}
}
