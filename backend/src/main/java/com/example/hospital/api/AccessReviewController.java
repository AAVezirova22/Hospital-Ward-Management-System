package com.example.hospital.api;

import com.example.hospital.service.AccessReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces/hospitals/{hospitalId}/access-review")
public class AccessReviewController {
  private final AccessReviewService reviews;

  public AccessReviewController(AccessReviewService reviews) {
    this.reviews = reviews;
  }

  public record ReviewInput(@NotBlank @Size(max = 20) String outcome, @Size(max = 300) String note) {}

  @GetMapping
  public Object report(
      @PathVariable long hospitalId, @RequestParam(defaultValue = "90") int inactiveAfterDays) {
    return reviews.report(hospitalId, inactiveAfterDays);
  }

  @PostMapping("/{userId}")
  public Object record(
      @PathVariable long hospitalId, @PathVariable long userId, @Valid @RequestBody ReviewInput input) {
    return reviews.record(hospitalId, userId, input.outcome(), input.note());
  }
}
