package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.domain.Room;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class RoomCapabilityMatcher {
  private static final int MAX_CAPABILITIES = 30;
  private static final int MAX_CAPABILITY_LENGTH = 64;

  private RoomCapabilityMatcher() {}

  public static Set<String> normalize(Collection<String> capabilities) {
    if (capabilities == null || capabilities.isEmpty()) return Set.of();
    if (capabilities.size() > MAX_CAPABILITIES) throw invalid();
    var normalized = new TreeSet<String>();
    for (var capability : capabilities) {
      if (capability == null) throw invalid();
      var value = capability.trim().toLowerCase(Locale.ROOT);
      if (value.isEmpty() || value.length() > MAX_CAPABILITY_LENGTH) throw invalid();
      normalized.add(value);
    }
    return java.util.Collections.unmodifiableSet(normalized);
  }

  public static Set<String> parse(String capabilities) {
    if (capabilities == null || capabilities.isBlank()) return Set.of();
    return normalize(List.of(capabilities.split(",", -1)));
  }

  public static List<String> missing(Collection<String> required, Collection<String> available) {
    var have = normalize(available);
    return normalize(required).stream().filter(capability -> !have.contains(capability)).toList();
  }

  public static void require(Room room, Collection<String> required) {
    require(room.getRoomNumber(), room.getCapabilities(), required);
  }

  public static void require(
      String roomNumber, Collection<String> available, Collection<String> required) {
    var missing = missing(required, available);
    if (!missing.isEmpty())
      throw ApiException.conflict(
          "ROOM_CAPABILITY_MISMATCH",
          "Room "
              + roomNumber
              + " is missing required capabilities: "
              + String.join(", ", missing)
              + ".");
  }

  private static ApiException invalid() {
    return new ApiException(
        400,
        "INVALID_ROOM_CAPABILITIES",
        "Use at most "
            + MAX_CAPABILITIES
            + " non-empty capability tags, each no longer than "
            + MAX_CAPABILITY_LENGTH
            + " characters.");
  }
}
