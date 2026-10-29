package com.cadence.http;

import java.util.Arrays;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Bounded offset pagination with a unique tie-breaker for equal primary sort values. */
public final class ApiPagination {
  public static final int MAX_PAGE = 10_000;
  public static final int MAX_SIZE = 100;

  private ApiPagination() {}

  public static PageRequest request(
      int page, int size, String field, String direction, String... allowedFields) {
    if (page < 0
        || page > MAX_PAGE
        || size < 1
        || size > MAX_SIZE
        || Arrays.stream(allowedFields).noneMatch(field::equals)) {
      throw new IllegalArgumentException("Invalid pagination parameters");
    }
    Sort.Direction order = Sort.Direction.fromString(direction);
    return PageRequest.of(page, size, Sort.by(order, field).and(Sort.by(order, "id")));
  }
}
