package com.example.hospital.api;

import com.example.hospital.security.Actor;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final Actor actor;

  public AuthController(Actor actor) {
    this.actor = actor;
  }

  @GetMapping("/auth/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/auth/me")
  public Object me() {
    return Views.account(actor.user());
  }
}
