package com.cadence.application.exceptions;

public class WorkspaceQuotaExceededException extends RuntimeException {
  public WorkspaceQuotaExceededException() {
    super("Workspace quota exceeded");
  }
}
