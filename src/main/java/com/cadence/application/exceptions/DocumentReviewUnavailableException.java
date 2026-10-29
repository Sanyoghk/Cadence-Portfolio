package com.cadence.application.exceptions;

public final class DocumentReviewUnavailableException extends RuntimeException {
  public DocumentReviewUnavailableException() {
    super("Document review unavailable");
  }
}
