package com.example.hospital.security;

import java.util.function.Supplier;

/** Request-local scope, also used by Hibernate's automatically enabled filter. */
public final class DepartmentContext implements Supplier<Long> {
  public record Scope(long id, String role, Long doctorId) {}
  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  public static Scope current() { return CURRENT.get(); }
  public static void set(Scope scope) { CURRENT.set(scope); }
  public static void clear() { CURRENT.remove(); }
  public static long id() { return CURRENT.get() == null ? -1L : CURRENT.get().id(); }
  @Override public Long get() { return id(); }
}
