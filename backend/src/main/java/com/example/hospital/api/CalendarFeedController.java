package com.example.hospital.api;

import com.example.hospital.service.CalendarFeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarFeedController {
  private final CalendarFeedService feeds;

  public CalendarFeedController(CalendarFeedService feeds) {
    this.feeds = feeds;
  }

  @PostMapping("/feed")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create() {
    return feeds.create();
  }

  @DeleteMapping("/feed")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke() {
    feeds.revoke();
  }

  /** Public by design: the unguessable token is the credential calendar apps can send. */
  @GetMapping("/feeds/{token}.ics")
  public ResponseEntity<String> feed(@PathVariable String token) {
    return feeds.render(token)
        .map(ics -> ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
            .header("Cache-Control", "no-store")
            .body(ics))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }
}
