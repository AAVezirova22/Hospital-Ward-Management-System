package com.example.hospital.api;

import com.example.hospital.service.ExternalIdentifierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external-ids")
public class ExternalIdentifierController {
  private final ExternalIdentifierService identifiers;

  public ExternalIdentifierController(ExternalIdentifierService identifiers) {
    this.identifiers = identifiers;
  }

  public record LinkInput(
      @NotBlank @Size(max = 30) String entityType,
      @NotNull Long entityId,
      @NotBlank @Size(max = 100) String namespace,
      @NotBlank @Size(max = 200) String value,
      @Size(max = 100) String source) {}

  @GetMapping
  public Object forRecord(@RequestParam String entityType, @RequestParam long entityId) {
    return identifiers.forRecord(entityType, entityId);
  }

  @GetMapping("/resolve")
  public Object resolve(@RequestParam String entityType, @RequestParam String namespace, @RequestParam String value) {
    return identifiers.resolve(entityType, namespace, value);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Object link(@Valid @RequestBody LinkInput input) {
    return identifiers.link(input.entityType(), input.entityId(), input.namespace(), input.value(), input.source());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(@PathVariable long id) {
    identifiers.unlink(id);
  }
}
