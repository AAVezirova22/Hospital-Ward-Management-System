package com.example.hospital.api;

import com.example.hospital.service.CsvImportService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Previewed CSV import for initial setup (#161). Administrator-only: preview
 * writes nothing operational, and commit is a separate explicit call.
 */
@RestController
@RequestMapping("/api/v1/csv-imports")
public class CsvImportController {
  private final CsvImportService imports;

  public CsvImportController(CsvImportService imports) {
    this.imports = imports;
  }

  @GetMapping("/templates")
  public Object templates() {
    return imports.templates();
  }

  /**
   * Takes the file as multipart, matching the AI source upload. The size cap is
   * enforced here before the content reaches the service, so an oversized file
   * is never staged.
   */
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Object preview(
      @RequestParam("entityType") String entityType,
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "duplicatePolicy", required = false) String duplicatePolicy)
      throws java.io.IOException {
    if (file == null || file.isEmpty())
      throw new ApiException(400, "EMPTY_FILE", "Attach a CSV file.");
    if (file.getSize() > CsvImportService.MAX_BYTES)
      throw new ApiException(400, "FILE_TOO_LARGE", "Import at most 2 MB per file.");
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
    return imports.preview(entityType, file.getOriginalFilename(), content, duplicatePolicy);
  }

  @GetMapping
  public Object batches(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return imports.batches(page, size);
  }

  @GetMapping("/{batchId}")
  public Object detail(@PathVariable long batchId) {
    return imports.detail(batchId);
  }

  @PostMapping("/{batchId}/commit")
  public Object commit(@PathVariable long batchId) {
    return imports.commit(batchId);
  }

  @PostMapping("/{batchId}/discard")
  public Object discard(@PathVariable long batchId) {
    return imports.discard(batchId);
  }
}
