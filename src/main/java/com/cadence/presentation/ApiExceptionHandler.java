package com.cadence.presentation;

import com.cadence.application.exceptions.*;
import com.cadence.http.ApiProblems;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(CreatorReportUnavailableException.class)
  ProblemDetail handleCreatorReportUnavailable(CreatorReportUnavailableException failure) {
    return ApiProblems.create(
        503,
        "report_unavailable",
        "Report unavailable",
        "Report service is busy or unavailable; try again later",
        true);
  }

  @ExceptionHandler(RecommendationUnavailableException.class)
  ProblemDetail handleRecommendationUnavailable(RecommendationUnavailableException failure) {
    return ApiProblems.create(
        503,
        "recommendation_unavailable",
        "Recommendation unavailable",
        "Bounded recommendation operation unavailable",
        true);
  }

  @ExceptionHandler(PerformanceUnavailableException.class)
  ProblemDetail handlePerformanceUnavailable(PerformanceUnavailableException failure) {
    return ApiProblems.create(
        503,
        "performance_unavailable",
        "Performance unavailable",
        "Bounded performance query unavailable; try again later",
        true);
  }

  @ExceptionHandler(MetricImportUnavailableException.class)
  ProblemDetail handleMetricImportUnavailable(MetricImportUnavailableException failure) {
    return ApiProblems.create(
        503,
        "metrics_import_unavailable",
        "Metrics import unavailable",
        "Metrics parsing capacity unavailable; try again later",
        true);
  }

  @ExceptionHandler(MediaUnavailableException.class)
  ProblemDetail handleMediaUnavailable(MediaUnavailableException failure) {
    return ApiProblems.create(
        503,
        "media_unavailable",
        "Media unavailable",
        "Media prerequisites could not be verified",
        true);
  }

  @ExceptionHandler(GenerationBudgetExceededException.class)
  ProblemDetail handleGenerationBudget(GenerationBudgetExceededException failure) {
    return ApiProblems.create(
        429,
        "generation_budget_exceeded",
        "Generation budget exceeded",
        "Generation cannot be admitted under the current budget",
        true);
  }

  @ExceptionHandler(DocumentReviewUnavailableException.class)
  ProblemDetail handleDocumentReviewUnavailable(DocumentReviewUnavailableException failure) {
    return ApiProblems.create(
        503,
        "document_review_unavailable",
        "Document review unavailable",
        "Document review prerequisites could not be verified; try again later",
        true);
  }

  @ExceptionHandler(StrategyGenerationUnavailableException.class)
  ProblemDetail handleStrategyGenerationUnavailable(
      StrategyGenerationUnavailableException exception) {
    return ApiProblems.create(
        503,
        "strategy_generation_unavailable",
        "Strategy generation unavailable",
        "Strategy generation could not be completed; try again later",
        true);
  }

  @ExceptionHandler(BrandAnalysisUnavailableException.class)
  ProblemDetail handleBrandAnalysisUnavailable(BrandAnalysisUnavailableException exception) {
    return ApiProblems.create(
        503,
        "brand_analysis_unavailable",
        "Brand analysis unavailable",
        "Brand analysis could not be completed; try again later",
        true);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
    Throwable cause = exception;
    for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
      if (cause
          instanceof com.cadence.security.MultipartAdmissionFilter.BodyLimitExceededException) {
        return ApiProblems.status(413);
      }
    }
    return ApiProblems.create(
        400, "malformed_body", "Invalid request", "Request body is malformed or unreadable", false);
  }

  @ExceptionHandler({EvidenceQuotaExceededException.class, WorkspaceQuotaExceededException.class})
  ProblemDetail handleEvidenceQuota(RuntimeException exception) {
    return ApiProblems.status(429);
  }

  @ExceptionHandler(EvidenceMalwareRejectedException.class)
  ProblemDetail handleMalwareRejection(EvidenceMalwareRejectedException exception) {
    return ApiProblems.create(
        400,
        "evidence_malware_rejected",
        "Invalid request",
        "Evidence file failed the malware scan",
        false);
  }

  @ExceptionHandler(EvidenceStorageUnavailableException.class)
  ProblemDetail handleStorageUnavailable(EvidenceStorageUnavailableException exception) {
    return ApiProblems.create(
        503,
        "evidence_storage_unavailable",
        "Evidence storage unavailable",
        "Evidence file could not be stored; try again later",
        true);
  }

  @ExceptionHandler(EvidenceScanUnavailableException.class)
  ProblemDetail handleScanUnavailable(EvidenceScanUnavailableException exception) {
    return ApiProblems.create(
        503,
        "evidence_scan_unavailable",
        "Evidence scanning unavailable",
        "Evidence file could not be scanned; try again later",
        true);
  }

  @ExceptionHandler(SocialEvidenceUnavailableException.class)
  ProblemDetail handleSocialUnavailable(SocialEvidenceUnavailableException exception) {
    return ApiProblems.create(
        503,
        "social_evidence_unavailable",
        "Social evidence unavailable",
        "Social evidence could not be retrieved; try again later",
        true);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail handleNotFound(ResourceNotFoundException exception) {
    // Preserve established resource descriptions only when they are fixed application constants.
    String detail =
        switch (exception.getMessage() == null ? "" : exception.getMessage()) {
          case "Job not found", "Brand not found", "Workspace not found", "Evidence not found" ->
              exception.getMessage();
          default -> "Resource not found";
        };
    return ApiProblems.create(404, "resource_not_found", "Resource not found", detail, false);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
    List<ApiProblems.Violation> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new ApiProblems.Violation(safeField(error.getField()), validationCode(error)))
            .distinct()
            .sorted(
                Comparator.comparing(ApiProblems.Violation::field)
                    .thenComparing(ApiProblems.Violation::code))
            .limit(50)
            .toList();
    return ApiProblems.validation(errors);
  }

  private String safeField(String field) {
    return field.matches("[A-Za-z][A-Za-z0-9_.\\[\\]]{0,127}") ? field : "request";
  }

  private String validationCode(FieldError error) {
    return switch (error.getCode() == null ? "" : error.getCode()) {
      case "NotNull", "NotBlank", "NotEmpty" -> "required";
      case "Size" -> "size";
      case "PositiveOrZero" -> "non_negative";
      case "typeMismatch" -> "type_mismatch";
      default -> "invalid";
    };
  }

  @ExceptionHandler({IllegalArgumentException.class, TypeMismatchException.class})
  ProblemDetail handleBadRequest(Exception exception) {
    return ApiProblems.status(400);
  }

  @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class})
  ProblemDetail handleConflict(RuntimeException exception) {
    return ApiProblems.status(409);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleForbidden(AccessDeniedException exception) {
    return ApiProblems.status(403);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
    return ApiProblems.status(413);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleOther(Exception exception) {
    if (exception instanceof ErrorResponse framework) {
      var headers = new HttpHeaders();
      headers.putAll(framework.getHeaders());
      var problem = ApiProblems.status(framework.getStatusCode().value());
      return new ResponseEntity<>(problem, headers, problem.getStatus());
    }
    var problem = ApiProblems.status(500);
    // Exception messages, causes, SQL and request values can contain credentials or personal data.
    LOG.error(
        "Unhandled HTTP failure type={} instance={}",
        exception.getClass().getName(),
        problem.getInstance());
    return ResponseEntity.status(500).body(problem);
  }
}
