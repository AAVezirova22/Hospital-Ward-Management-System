package com.example.hospital.service;

import com.example.hospital.domain.BedHold;
import java.time.Instant;
import java.util.List;

final class BedHoldCapacity {
  private BedHoldCapacity() {}

  static int reserved(List<BedHold> holds, Instant from) {
    return peak(holds, from, null);
  }

  static int peakDuring(List<BedHold> holds, Instant start, Instant end) {
    return peak(holds, start, end);
  }

  static int active(List<BedHold> holds, Instant at) {
    return holds.stream().filter(hold -> covers(hold, at)).mapToInt(BedHold::getBedCount).sum();
  }

  private static int peak(List<BedHold> holds, Instant start, Instant end) {
    var points = new java.util.TreeSet<Instant>();
    points.add(start);
    holds.stream()
        .map(BedHold::getStartsAt)
        .filter(point -> point.isAfter(start) && (end == null || point.isBefore(end)))
        .forEach(points::add);
    return points.stream().mapToInt(point -> active(holds, point)).max().orElse(0);
  }

  private static boolean covers(BedHold hold, Instant at) {
    return hold.getCancelledAt() == null
        && !hold.getStartsAt().isAfter(at)
        && hold.getEndsAt().isAfter(at);
  }
}
