package com.example.hospital.api;

import java.util.List;

public record PagedResult<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    Integer nextPage) {
  /** Same shape from a Spring Data page, keeping its own total-page count. */
  public static <T> PagedResult<T> of(org.springframework.data.domain.Page<T> page) {
    boolean hasNext = page.hasNext();
    return new PagedResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
        page.getTotalPages(), hasNext, hasNext ? page.getNumber() + 1 : null);
  }

  public static <T> PagedResult<T> of(List<T> items, int page, int size, long totalElements) {
    int totalPages = (int) Math.min((totalElements + size - 1) / size, Integer.MAX_VALUE);
    boolean hasNext = page + 1L < totalPages;
    return new PagedResult<>(items, page, size, totalElements, totalPages, hasNext,
        hasNext ? page + 1 : null);
  }
}
