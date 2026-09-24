package com.example.hospital.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adds navigation to every paged collection response (#397): an RFC 8288 {@code Link} header with
 * {@code first}, {@code prev}, {@code next} and {@code last} relations, and {@code X-Total-Count}.
 * Links are relative and keep the request's other filters.
 */
@RestControllerAdvice
public class PageLinks implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
    if (body instanceof PagedResult<?> page)
      apply(response.getHeaders(), request.getURI(), page.page(), page.size(), page.totalElements());
    return body;
  }

  /** Writes the headers for a page; also used by collection endpoints with their own body shape. */
  public static void apply(HttpHeaders headers, URI uri, int page, int size, long total) {
    int lastPage = total == 0 ? 0 : (int) Math.min((total - 1) / Math.max(size, 1), Integer.MAX_VALUE);
    List<String> links = new ArrayList<>();
    links.add(link(uri, 0, size, "first"));
    if (page > 0) links.add(link(uri, Math.min(page - 1, lastPage), size, "prev"));
    if (page < lastPage) links.add(link(uri, page + 1, size, "next"));
    links.add(link(uri, lastPage, size, "last"));
    headers.set(HttpHeaders.LINK, String.join(", ", links));
    headers.set("X-Total-Count", Long.toString(total));
  }

  private static String link(URI uri, int page, int size, String rel) {
    String target = UriComponentsBuilder.fromUri(uri)
        .scheme(null).host(null).port(-1)
        .replaceQueryParam("page", page)
        .replaceQueryParam("size", size)
        .build()
        .toUriString();
    return "<" + target + ">; rel=\"" + rel + "\"";
  }
}
