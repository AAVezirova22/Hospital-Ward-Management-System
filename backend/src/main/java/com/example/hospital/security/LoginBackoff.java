package com.example.hospital.security;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginBackoff {
  private record State(int failures, long lockedUntil) {}

  private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

  public boolean blocked(String username) {
    var state = states.get(key(username));
    return state != null && state.lockedUntil > System.currentTimeMillis();
  }

  public void failure(String username) {
    states.compute(
        key(username),
        (ignored, previous) -> {
          int count = previous == null ? 1 : previous.failures + 1;
          long lock =
              count >= 8
                  ? System.currentTimeMillis() + 300_000
                  : count >= 5 ? System.currentTimeMillis() + 30_000 : 0L;
          return new State(count, lock);
        });
  }

  public void success(String username) {
    states.remove(key(username));
  }

  private static String key(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }
}
