package com.cadence.domain;

final class DomainText {

  private DomainText() {}

  static String required(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }

    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldName + " must not exceed " + maxLength + " characters");
    }
    return normalized;
  }

  static String optional(String value, String fieldName, int maxLength) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldName + " must not exceed " + maxLength + " characters");
    }
    return normalized;
  }
}
