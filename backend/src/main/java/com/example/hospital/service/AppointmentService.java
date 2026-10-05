package com.example.hospital.service;

import com.example.hospital.api.*;
import com.example.hospital.domain.*;
import com.example.hospital.repository.*;
import com.example.hospital.security.Actor;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
  private final DoctorAppointmentRepository appointments;
  private final DoctorRepository doctors;
  private final WorkflowLockRepository lock;
  private final Actor actor;
  private final DepartmentTimeService time;
  private final AuditService audit;

  public AppointmentService(DoctorAppointmentRepository appointments, DoctorRepository doctors,
      WorkflowLockRepository lock, Actor actor, DepartmentTimeService time, AuditService audit) {
    this.appointments = appointments;
    this.doctors = doctors;
    this.lock = lock;
    this.actor = actor;
    this.time = time;
    this.audit = audit;
  }

  public record Window(Instant startsAt, Instant endsAt, int durationMinutes) {}

  public Doctor doctor(Long id) {
    var user = actor.user();
    if (user.getRole().equals("DOCTOR")) {
      if (!Objects.equals(id, user.getDoctorId())) throw new AccessDeniedException("Own schedule only");
    } else actor.staff();
    return doctors.findById(id).orElseThrow(ApiException::missing);
  }

  public Window window(String value, Integer duration) {
    int minutes = duration == null ? 30 : duration;
    if (minutes < 5 || minutes > 240)
      throw new ApiException(400, "INVALID_APPOINTMENT_DURATION", "Choose a duration between 5 and 240 minutes.");
    Instant start;
    try {
      if (value == null || value.isBlank()) throw new DateTimeParseException("Missing time", "", 0);
      try { start = OffsetDateTime.parse(value.trim()).toInstant(); }
      catch (DateTimeParseException e) {
        var local = LocalDateTime.parse(value.trim());
        var offsets = time.zoneId().getRules().getValidOffsets(local);
        if (offsets.size() != 1)
          throw new ApiException(400, "AMBIGUOUS_APPOINTMENT_TIME",
              "This time is skipped or repeated by daylight saving. Choose another time or supply an explicit UTC offset.");
        start = local.toInstant(offsets.getFirst());
      }
    } catch (DateTimeParseException e) {
      throw new ApiException(400, "INVALID_APPOINTMENT_TIME", "Provide a full date and time, for example 2026-11-02T16:30.");
    }
    start = start.truncatedTo(ChronoUnit.MICROS);
    if (!start.isAfter(Instant.now()))
      throw new ApiException(400, "APPOINTMENT_IN_PAST", "Choose an appointment time in the future.");
    return new Window(start, start.plusSeconds(minutes * 60L), minutes);
  }

  public Map<String, Object> availability(Long doctorId, String startsAt, Integer duration) {
    var doctor = doctor(doctorId);
    var window = window(startsAt, duration);
    var conflicts = appointments.overlapping(doctorId, window.startsAt(), window.endsAt());
    var result = details(doctor, window);
    result.put("available", doctor.isActive() && conflicts.isEmpty());
    result.put("reason", !doctor.isActive() ? "Doctor is inactive."
        : conflicts.isEmpty() ? "No overlapping appointment."
        : "This doctor already has an appointment during that time.");
    // Availability does not disclose other attendees' names or contact information.
    result.put("conflicts", conflicts.stream().map(a -> Map.of(
        "startsAt", a.getStartsAt(), "endsAt", a.getEndsAt())).toList());
    return result;
  }

  public Map<String, Object> preview(AppointmentInput input) {
    actor.staff();
    validateText(input);
    var result = availability(input.doctorId(), input.startsAt(), input.durationMinutes());
    requireAvailable(result);
    result.put("attendeeName", input.attendeeName().trim());
    result.put("contact", clean(input.contact()));
    result.put("notes", clean(input.notes()));
    return result;
  }

  @Transactional
  public Map<String, Object> create(AppointmentInput input, String source) {
    actor.staff();
    // All writers, including doctor deactivation, share the department workflow lock.
    // Recheck under the lock: parallel requests cannot reserve overlapping windows.
    lock.acquire();
    validateText(input);
    var doctor = doctor(input.doctorId());
    var window = window(input.startsAt(), input.durationMinutes());
    if (!doctor.isActive()) throw ApiException.conflict("DOCTOR_INACTIVE", "Appointments require an active doctor.");
    if (!appointments.overlapping(doctor.getId(), window.startsAt(), window.endsAt()).isEmpty())
      throw ApiException.conflict("APPOINTMENT_CONFLICT", "This doctor already has an appointment during that time. Choose another time.");
    var appointment = new DoctorAppointment();
    appointment.setDoctorId(doctor.getId());
    appointment.setAttendeeName(input.attendeeName().trim());
    appointment.setContact(clean(input.contact()));
    appointment.setNotes(clean(input.notes()));
    appointment.setStartsAt(window.startsAt());
    appointment.setEndsAt(window.endsAt());
    appointment.setCreatedBy(actor.user().getId());
    appointments.saveAndFlush(appointment);
    audit.log("APPOINTMENT_BOOKED", "DoctorAppointment", appointment.getId(), source);
    return view(appointment, doctor);
  }

  private void requireAvailable(Map<String, Object> availability) {
    if (!Boolean.TRUE.equals(availability.get("available"))) {
      var d = (Map<?, ?>) availability.get("doctor");
      throw ApiException.conflict(Boolean.TRUE.equals(d.get("active")) ? "APPOINTMENT_CONFLICT" : "DOCTOR_INACTIVE",
          String.valueOf(availability.get("reason")));
    }
  }

  private void validateText(AppointmentInput input) {
    if (input.doctorId() == null || input.doctorId() <= 0 || input.attendeeName() == null
        || input.attendeeName().isBlank() || input.attendeeName().length() > 120
        || clean(input.contact()).length() > 120 || clean(input.notes()).length() > 500)
      throw new ApiException(400, "INVALID_APPOINTMENT", "Provide a booking name and valid appointment details.");
  }

  @Transactional
  public Map<String, Object> cancel(Long id, long version) {
    actor.staff();
    lock.acquire();
    var appointment = appointments.findById(id).orElseThrow(ApiException::missing);
    if (appointment.getCancelledAt() != null) return view(appointment, doctor(appointment.getDoctorId()));
    if (appointment.getVersion() != version)
      throw ApiException.conflict("STALE_APPOINTMENT", "This appointment changed. Refresh before cancelling.");
    appointment.setCancelledAt(Instant.now());
    appointment.setCancelledBy(actor.user().getId());
    appointments.saveAndFlush(appointment);
    audit.log("APPOINTMENT_CANCELLED", "DoctorAppointment", id, "UI");
    return view(appointment, doctor(appointment.getDoctorId()));
  }

  public Object list(Long doctorId, LocalDate from, LocalDate to, String status, String query, int page, int size) {
    var user = actor.user();
    if (user.getRole().equals("DOCTOR")) {
      if (doctorId != null && !Objects.equals(doctorId, user.getDoctorId()))
        throw new AccessDeniedException("Own schedule only");
      doctorId = user.getDoctorId();
      if (doctorId == null) throw new AccessDeniedException("Doctor identity required");
    } else actor.staff();
    if (doctorId != null) doctor(doctorId);
    if (from != null && to != null && from.isAfter(to))
      throw new ApiException(400, "INVALID_APPOINTMENT_RANGE", "Choose an end date on or after the start date.");
    if (!Set.of("ALL", "SCHEDULED", "CANCELLED").contains(status))
      throw new ApiException(400, "INVALID_APPOINTMENT_STATUS", "Choose scheduled, cancelled or all appointments.");
    var zone = time.zoneId();
    var start = (from == null ? LocalDate.of(1900, 1, 1) : from).atStartOfDay(zone).toInstant();
    var end = (to == null ? LocalDate.of(9999, 12, 30) : to).plusDays(1).atStartOfDay(zone).toInstant();
    String pattern = "%" + clean(query).toLowerCase(Locale.ROOT).replace("!", "!!")
        .replace("%", "!%").replace("_", "!_") + "%";
    var result = appointments.directory(doctorId, start, end, status, pattern,
        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by("startsAt", "id")));
    var ids = result.getContent().stream().map(DoctorAppointment::getDoctorId).distinct().toList();
    var directory = new HashMap<Long, Doctor>();
    doctors.findAllById(ids).forEach(d -> directory.put(d.getId(), d));
    var views = result.map(a -> view(a, directory.get(a.getDoctorId())));
    return Map.of("appointments", PagedResult.of(views), "timeZone", time.timeZone());
  }

  private Map<String, Object> details(Doctor doctor, Window window) {
    var result = new LinkedHashMap<String, Object>();
    result.put("doctor", Views.doctor(doctor));
    result.put("startsAt", window.startsAt());
    result.put("endsAt", window.endsAt());
    result.put("durationMinutes", window.durationMinutes());
    result.put("timeZone", time.timeZone());
    return result;
  }

  public Map<String, Object> view(DoctorAppointment a, Doctor doctor) {
    var result = details(doctor, new Window(a.getStartsAt(), a.getEndsAt(),
        (int) Duration.between(a.getStartsAt(), a.getEndsAt()).toMinutes()));
    result.put("id", a.getId());
    result.put("version", a.getVersion());
    result.put("attendeeName", a.getAttendeeName());
    result.put("contact", a.getContact());
    result.put("notes", a.getNotes());
    result.put("status", a.getCancelledAt() == null ? "SCHEDULED" : "CANCELLED");
    result.put("cancelledAt", a.getCancelledAt());
    result.put("createdAt", a.getCreatedAt());
    return result;
  }

  private static String clean(String value) { return value == null ? "" : value.trim(); }
}
