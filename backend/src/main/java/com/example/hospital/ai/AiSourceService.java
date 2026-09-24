package com.example.hospital.ai;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.*;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Short-lived, bounded source context. Original bytes are never retained. */
@Service
public class AiSourceService {
  public static final int MAX_BYTES = 5 * 1024 * 1024, MAX_TEXT = 40000;
  private final Actor actor;
  private final Map<String, Source> sources = new HashMap<>();
  public record Source(String id, long userId, long departmentId, String name, String text, Instant expiresAt) {}
  public AiSourceService(Actor actor) { this.actor = actor; }

  private void purge() {
    sources.values().removeIf(s -> !s.expiresAt().isAfter(Instant.now()));
  }

  public synchronized Map<String, Object> upload(MultipartFile file) {
    purge();
    long owner = actor.user().getId();
    if (file.isEmpty() || file.getSize() > MAX_BYTES)
      throw new ApiException(400, "FILE_SIZE", "Choose a nonempty file up to 5 MB.");
    if (sources.size() >= 500 || sources.values().stream().filter(s -> s.userId() == owner).count() >= 20)
      throw new ApiException(429, "SOURCE_LIMIT", "Remove attached files before adding more.");
    String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document")
        .replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    if (name.length() > 200 || !name.toLowerCase(Locale.ROOT).matches(".+\\.(txt|md|csv|tsv|json|pdf|docx|xlsx|pptx|odt|ods|rtf)"))
      throw new ApiException(400, "FILE_TYPE", "Use text, CSV, JSON, PDF, Word, Excel, PowerPoint, OpenDocument or RTF files.");
    String text;
    try (var stream = file.getInputStream()) {
      var handler = new BodyContentHandler(MAX_TEXT);
      var metadata = new Metadata();
      metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
      var context = new ParseContext();
      var pdf = new PDFParserConfig();
      pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
      context.set(PDFParserConfig.class, pdf);
      context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
        public boolean shouldParseEmbedded(Metadata m) { return false; }
        public void parseEmbedded(InputStream i, org.xml.sax.ContentHandler h, Metadata m, boolean outputHtml) {}
      });
      new AutoDetectParser().parse(stream, handler, metadata, context);
      text = handler.toString().strip();
    } catch (Exception e) {
      throw new ApiException(400, "FILE_UNREADABLE", "Cannot extract this file completely. It may be encrypted, damaged or exceed 40,000 text characters. Split it into smaller files.");
    }
    if (text.isBlank())
      throw new ApiException(400, "FILE_EMPTY", "No readable text found. Scanned images need OCR before uploading.");
    String id = UUID.randomUUID().toString();
    var source = new Source(id, owner, DepartmentContext.id(), name, text, Instant.now().plusSeconds(1800));
    sources.put(id, source);
    return Map.of("id", id, "name", name, "characters", text.length(), "expiresAt", source.expiresAt());
  }

  public synchronized List<Map<String, String>> context(List<String> ids) {
    purge();
    if (ids == null) return List.of();
    if (ids.size() > 20) throw new IllegalArgumentException();
    int total = 0;
    List<Map<String, String>> result = new ArrayList<>();
    for (String id : new LinkedHashSet<>(ids)) {
      var s = owned(id);
      total += s.text().length();
      if (total > 120000)
        throw new ApiException(400, "CONTEXT_LIMIT", "Use fewer files per request (120,000 text characters maximum).");
      result.add(Map.of("id", s.id(), "name", s.name(), "text", s.text()));
    }
    return result;
  }

  /** Returns source text only to an explicitly invoked feature after enforcing the same owner/scope/expiry rules. */
  public synchronized Source sourceForExtraction(String id) {
    purge();
    return owned(id);
  }

  private Source owned(String id) {
    var s = sources.get(id);
    if (s == null || s.userId() != actor.user().getId() || s.departmentId() != DepartmentContext.id())
      throw new ApiException(404, "SOURCE_UNAVAILABLE", "This attachment expired or is unavailable in this workspace. Attach it again.");
    return s;
  }

  public synchronized void remove(String id) { purge(); owned(id); sources.remove(id); }
}
