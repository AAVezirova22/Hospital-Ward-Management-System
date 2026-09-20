package com.example.hospital.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RequiredSecrets {
  public RequiredSecrets(@Value("${spring.datasource.password:}") String password) {
    if (password == null || password.isBlank() || "hospital-local-only".equals(password)) {
      throw new IllegalStateException(
          "DATABASE_PASSWORD must be set; the committed local fallback is not allowed.");
    }
  }
}
