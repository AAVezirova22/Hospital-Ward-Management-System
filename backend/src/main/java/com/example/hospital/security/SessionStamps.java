package com.example.hospital.security;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class SessionStamps {
  private static final SecureRandom RANDOM = new SecureRandom();

  private SessionStamps() {}

  public static String next() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
