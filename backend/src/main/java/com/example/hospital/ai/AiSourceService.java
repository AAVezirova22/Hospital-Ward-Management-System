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
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Short-lived, bounded source context. Original bytes are never retained. */
@Service
public class AiSourceService {
  public static final int MAX_BYTES = 5 * 1024 * 1024, MAX_TEXT = 40000;
  private final Actor actor;
  private final Map<String, Source> sources = new HashMap<>();
  public record LocationSpan(int start, int end, String location) {}
  public record Source(String id, long userId, long departmentId, String name, String text,
      Instant expiresAt, List<LocationSpan> locations) {
    /** Returns parser-derived structure only when the entire cited range maps to one supported structure. */
    public String locationFor(int start, int end) {
      if (start < 0 || end <= start || end > text.length()) return null;
      var found = new LinkedHashSet<String>();
      int coveredUntil = start;
      for (var span : locations) {
        if (span.end() <= coveredUntil || span.start() >= end) continue;
        if (span.start() > coveredUntil) return null;
        found.add(span.location());
        coveredUntil = Math.min(end, span.end());
        if (coveredUntil == end) break;
      }
      if (found.isEmpty() || coveredUntil < end) return null;
      if (found.size() == 1) return found.iterator().next();
      if (found.size() <= 3) return String.join("; ", found);
      return "multiple source locations";
    }
  }
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
    List<LocationSpan> locations;
    try (var stream = file.getInputStream()) {
      var writer = new LocatedTextWriter();
      var body = new BodyContentHandler(writer);
      var handler = new StructuralLocationHandler(body, writer);
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
      int leading = 0;
      while (leading < writer.text.length() && Character.isWhitespace(writer.text.charAt(leading))) leading++;
      int trailing = writer.text.length();
      while (trailing > leading && Character.isWhitespace(writer.text.charAt(trailing - 1))) trailing--;
      text = writer.text.substring(leading, trailing);
      locations = writer.locations(leading, trailing);
    } catch (Exception e) {
      throw new ApiException(400, "FILE_UNREADABLE", "Cannot extract this file completely. It may be encrypted, damaged or exceed 40,000 text characters. Split it into smaller files.");
    }
    if (text.isBlank())
      throw new ApiException(400, "FILE_EMPTY", "No readable text found. Scanned images need OCR before uploading.");
    String id = UUID.randomUUID().toString();
    var source = new Source(id, owner, DepartmentContext.id(), name, text,
        Instant.now().plusSeconds(1800), locations);
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

  /** Records offsets in the exact text emitted by Tika while retaining only structural locations Tika exposes. */
  private static final class LocatedTextWriter extends Writer {
    private final StringBuilder text = new StringBuilder();
    private final List<LocationSpan> spans = new ArrayList<>();
    private String currentLocation;
    @Override public void write(char[] chars, int offset, int length) throws IOException {
      if (length <= 0) return;
      if (text.length() + length > MAX_TEXT) throw new IOException("Extracted text exceeds limit");
      int start = text.length();
      text.append(chars, offset, length);
      if (currentLocation == null) return;
      if (!spans.isEmpty()) {
        var previous = spans.getLast();
        if (previous.end() == start && previous.location().equals(currentLocation)) {
          spans.set(spans.size() - 1, new LocationSpan(previous.start(), text.length(), currentLocation));
          return;
        }
      }
      spans.add(new LocationSpan(start, text.length(), currentLocation));
    }
    @Override public void flush() {}
    @Override public void close() {}
    List<LocationSpan> locations(int leading, int trailing) {
      return spans.stream().map(span -> new LocationSpan(
          Math.max(0, span.start() - leading), Math.min(trailing, span.end()) - leading, span.location()))
          .filter(span -> span.end() > span.start()).toList();
    }
  }

  /** Tika emits page containers for PDFs and named sheet containers for XLSX. */
  private static final class StructuralLocationHandler extends ContentHandlerDecorator {
    private final LocatedTextWriter writer;
    private int page, sheet;
    private String structure;
    StructuralLocationHandler(org.xml.sax.ContentHandler delegate, LocatedTextWriter writer) {
      super(delegate); this.writer = writer;
    }
    @Override public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException {
      String tag = localName == null || localName.isEmpty() ? qName : localName;
      String classes = attributes == null ? "" : Optional.ofNullable(attributes.getValue("class")).orElse("");
      if ("page".equalsIgnoreCase(classes) && "div".equalsIgnoreCase(tag)) {
        structure = "PDF page " + (++page);
      } else if ("sheet".equalsIgnoreCase(classes) && "div".equalsIgnoreCase(tag)) {
        sheet++; structure = "spreadsheet sheet " + sheet;
      }
      writer.currentLocation = structure;
      super.startElement(uri, localName, qName, attributes);
    }
    @Override public void endElement(String uri, String localName, String qName) throws SAXException {
      super.endElement(uri, localName, qName);
      String tag = localName == null || localName.isEmpty() ? qName : localName;
      if ("div".equalsIgnoreCase(tag) && structure != null
          && (structure.startsWith("PDF page ") || structure.startsWith("spreadsheet sheet "))) {
        // Outer container end; nested non-structural divs do not exist in the supported Tika layouts.
        if (structure.startsWith("PDF page ")) structure = null;
        else if (structure.startsWith("spreadsheet sheet ")) { structure = null; sheet = 0; }
      }
      writer.currentLocation = structure;
    }
  }
}
