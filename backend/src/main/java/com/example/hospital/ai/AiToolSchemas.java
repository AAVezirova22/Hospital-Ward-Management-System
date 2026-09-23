package com.example.hospital.ai;

import java.util.List;
import java.util.Map;

public final class AiToolSchemas {
  private AiToolSchemas() {}

  public static final Map<String, List<String>> SCHEMAS =
      Map.ofEntries(
          Map.entry("searchPatients", List.of("query")),
          Map.entry("getPatientSummary", List.of("patientQuery")),
          Map.entry("getAvailableRooms", List.of("minimumFreeBeds", "requiredCapabilities")),
          Map.entry("getRoomOccupancy", List.of("minimumFreeBeds", "requiredCapabilities")),
          Map.entry("getDoctorPatients", List.of("doctorQuery")),
          Map.entry("getAdmission", List.of("admissionId")),
          Map.entry("getAdmissions", List.of("from", "to")),
          Map.entry("getProcedureStatistics", List.of("from", "to")),
          Map.entry("getDashboardSummary", List.of()),
          Map.entry("listWorkspaces", List.of()),
          Map.entry("prepareAdmission", List.of("patientQuery", "doctorQuery", "roomNumber", "requiredRoomCapabilities")),
          Map.entry("prepareTransfer", List.of("patientQuery", "roomNumber")),
          Map.entry("prepareDischarge", List.of("patientQuery")),
          Map.entry("navigate", List.of("route")),
          Map.entry("help", List.of()));
}
