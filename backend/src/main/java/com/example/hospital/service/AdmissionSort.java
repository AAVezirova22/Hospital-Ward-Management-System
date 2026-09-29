package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import java.util.List;
import java.util.Locale;

/**
 * Parsing and echoing of the admission register's sort (#164).
 *
 * <p>Kept apart from {@link StayService} because it is pure: given a string it
 * returns a validated key and direction, with no repository or security
 * dependency. That is what makes it straightforward to test directly.
 */
public final class AdmissionSort {
  private AdmissionSort() {}

  public static final List<String> KEYS = List.of("admissionDate", "patient", "room", "status");
  public static final String DEFAULT_KEY = "admissionDate";
  public static final String DEFAULT_DIRECTION = "desc";

  /**
   * Parses {@code key} or {@code key:direction}. A missing direction takes the
   * key's natural default rather than a blanket one: a register reads best newest
   * first by date, but by name or room ascending is what a person expects.
   */
  public static String[] parse(String requested) {
    if (requested == null || requested.isBlank()) return new String[] {DEFAULT_KEY, DEFAULT_DIRECTION};
    int separator = requested.indexOf(':');
    String key = (separator < 0 ? requested : requested.substring(0, separator)).strip();
    if (!KEYS.contains(key))
      throw new ApiException(400, "INVALID_SORT", "Sort by " + String.join(", ", KEYS) + ".");
    if (separator < 0) return new String[] {key, defaultDirection(key)};
    String direction = requested.substring(separator + 1).strip().toLowerCase(Locale.ROOT);
    if (!direction.equals("asc") && !direction.equals("desc"))
      throw new ApiException(400, "INVALID_SORT", "Sort direction must be asc or desc.");
    return new String[] {key, direction};
  }

  public static String defaultDirection(String key) {
    return key.equals(DEFAULT_KEY) ? DEFAULT_DIRECTION : "asc";
  }

  /** The sort the server actually applied, so a client can render the headers. */
  public static String describe(String key, String direction) {
    String effectiveKey =
        key == null || key.isBlank() ? DEFAULT_KEY : key.strip();
    String effectiveDirection =
        direction == null || direction.isBlank()
            ? defaultDirection(effectiveKey)
            : direction.strip().toLowerCase(Locale.ROOT);
    return effectiveKey + ":" + effectiveDirection;
  }
}
