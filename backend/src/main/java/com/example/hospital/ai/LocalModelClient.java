package com.example.hospital.ai;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Offline, deterministic command interpretation; never claims to be a generative model. */
public class LocalModelClient implements AiModelClient {
  public String identifier() {
    return "local-command-model";
  }

  static String capture(String text, String regex) {
    var m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
    return m.find() ? m.group(1).trim() : "";
  }

  public ToolCall complete(String message, Context ctx) {
    String s = message.trim().replaceAll("[.!?]+$", ""), l = s.toLowerCase(Locale.ROOT);
    Map<String, String> a = new HashMap<>();
    if (l.matches(
        ".*(password|execute|delete from|ignore"
            + " previous|medication|diagnos|treatment|triage|administrator access|shell|grant"
            + " admin).*")) return new ToolCall("help", Map.of());
    var booking = Pattern.compile(
        "^(?:book|prepare) appointment with (.+?) on (\\S+) for (.+?)(?: duration (\\d+))?$",
        Pattern.CASE_INSENSITIVE).matcher(s);
    if (booking.matches()) {
      a.put("doctorQuery", booking.group(1));
      a.put("startsAt", booking.group(2));
      a.put("attendeeName", booking.group(3));
      if (booking.group(4) != null) a.put("durationMinutes", booking.group(4));
      return new ToolCall("prepareAppointment", a);
    }
    var availability = Pattern.compile(
        "^check availability (?:for |with )?(.+?) on (\\S+)(?: duration (\\d+))?$",
        Pattern.CASE_INSENSITIVE).matcher(s);
    if (availability.matches()) {
      a.put("doctorQuery", availability.group(1));
      a.put("startsAt", availability.group(2));
      if (availability.group(3) != null) a.put("durationMinutes", availability.group(3));
      return new ToolCall("getDoctorAvailability", a);
    }
    if (l.startsWith("appointments with ") || l.startsWith("show appointments with ")) {
      a.put("doctorQuery", s.replaceFirst("(?i)^(?:show )?appointments with ", ""));
      return new ToolCall("getDoctorAppointments", a);
    }
    if (l.startsWith("open ")) {
      String route = s.substring(5).toLowerCase();
      if (List.of(
              "rooms",
              "patients",
              "admissions",
              "doctors",
              "reports",
              "procedures",
              "dashboard",
              "users",
              "planner",
              "audit",
              "presentation")
          .contains(route)) return new ToolCall("navigate", Map.of("route", "/app/" + route));
    }
    if (l.startsWith("move ") || l.startsWith("transfer ")) {
      a.put("patientQuery", capture(s, "(?:move|transfer) (.+?) to "));
      a.put("roomNumber", capture(s, "room ([a-zA-Z0-9-]+)"));
      return new ToolCall("prepareTransfer", a);
    }
    if (l.startsWith("discharge ") || l.startsWith("prepare discharge")) {
      a.put("patientQuery", s.replaceFirst("(?i)^(?:prepare )?discharge\\s*", "").trim());
      return new ToolCall("prepareDischarge", a);
    }
    if (l.startsWith("admit ")) {
      a.put("patientQuery", capture(s, "admit (.+?)(?: with| doctor| to|$)"));
      a.put("doctorQuery", capture(s, "doctor (.+?)(?: room| to|$)"));
      a.put("roomNumber", capture(s, "room ([a-zA-Z0-9-]+)"));
      return new ToolCall("prepareAdmission", a);
    }
    if (l.contains("summar") || l.contains("history")) {
      a.put("patientQuery", capture(s, "(?:summary|history)(?: of| for)? (.+)$"));
      return new ToolCall("getPatientSummary", a);
    }
    if (l.contains("patient") && l.contains("dr.")) {
      a.put("doctorQuery", capture(s, "dr\\. (.+?)(?: patients|$)"));
      return new ToolCall("getDoctorPatients", a);
    }
    if (l.contains("procedure")
        && (l.contains("today")
            || l.contains("month")
            || l.contains("report")
            || l.contains("week"))) {
      var now = LocalDate.now(ZoneId.of(ctx.timeZone()));
      a.put(
          "from",
          l.contains("month")
              ? now.withDayOfMonth(1).toString()
              : l.contains("week") ? now.minusDays(6).toString() : now.toString());
      a.put("to", now.toString());
      return new ToolCall("getProcedureStatistics", a);
    }
    if (l.contains("admission") && (l.contains("week") || l.contains("today"))) {
      var now = LocalDate.now(ZoneId.of(ctx.timeZone()));
      a.put("from", l.contains("week") ? now.minusDays(6).toString() : now.toString());
      a.put("to", now.toString());
      return new ToolCall("getAdmissions", a);
    }
    if (l.contains("room") || l.contains("bed") || l.contains("capacity")) {
      String n = capture(l, "(?:least |with )(\\d+) (?:free|available|beds)");
      if (n.isBlank() && l.contains("two")) n = "2";
      a.put("minimumFreeBeds", n.isBlank() ? "0" : n);
      return new ToolCall(l.contains("occupancy") ? "getRoomOccupancy" : "getAvailableRooms", a);
    }
    if (l.startsWith("find ") || l.startsWith("show patient ") || l.startsWith("open ")) {
      a.put("query", s.replaceFirst("(?i)^(find|show patient|open) ", ""));
      return new ToolCall("searchPatients", a);
    }
    if (l.contains("workspace")
        || l.contains("which hospital")
        || l.contains("my hospital")
        || l.contains("list hospital")) return new ToolCall("listWorkspaces", Map.of());
    if (l.contains("department")
        || l.contains("dashboard")
        || l.contains("status")
        || l.contains("brief")) return new ToolCall("getDashboardSummary", Map.of());
    return new ToolCall("help", Map.of());
  }
}
