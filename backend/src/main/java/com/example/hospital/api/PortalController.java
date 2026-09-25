package com.example.hospital.api;
import com.example.hospital.security.Actor;
import com.example.hospital.service.HospitalService;
import com.example.hospital.service.PatientCorrectionService;
import com.example.hospital.service.PatientPortalContactService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/portal")
public class PortalController {
  private final Actor actor; private final HospitalService hospital; private final PatientCorrectionService corrections; private final PatientPortalContactService contacts;
  public PortalController(Actor actor,HospitalService hospital,PatientCorrectionService corrections,PatientPortalContactService contacts) {this.actor=actor;this.hospital=hospital;this.corrections=corrections;this.contacts=contacts;}
  @GetMapping("/me") public Object me() {return hospital.summary(actor.user().getPatientId());}
  @PutMapping("/me/contact") public Object updateContact(@RequestBody PatientPortalContactService.ContactInput input) { return contacts.update(input); }
  @GetMapping("/correction-requests") public Object corrections() { return corrections.mine(); }
  @PostMapping("/correction-requests") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object submitCorrection(@RequestBody PatientCorrectionService.RequestInput input) { return corrections.submit(input); }
}
