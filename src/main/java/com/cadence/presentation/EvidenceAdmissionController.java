package com.cadence.presentation;

import com.cadence.application.results.*;
import com.cadence.application.usecases.ReviewEvidenceUseCase;
import com.cadence.presentation.dto.AdmitEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/{evidenceId}/ai-admission")
@PreAuthorize("denyAll()")
public class EvidenceAdmissionController {
  private final ReviewEvidenceUseCase reviews;

  public EvidenceAdmissionController(ReviewEvidenceUseCase reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/history")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<com.cadence.presentation.dto.PageResponse<EvidenceAdmissionHistory>>
      history(
          Authentication authentication,
          @PathVariable UUID evidenceId,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "20") int size) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            com.cadence.presentation.dto.PageResponse.from(
                reviews.history(evidenceId, p.workspaceIds(), p.userId(), page, size),
                java.util.function.Function.identity()));
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<EvidenceReview> preview(
      Authentication authentication, @PathVariable UUID evidenceId) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.preview(evidenceId, p.workspaceIds(), p.userId()));
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<EvidenceAdmissionDetails> admit(
      Authentication authentication,
      @PathVariable UUID evidenceId,
      @Valid @RequestBody AdmitEvidenceRequest request) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(
            reviews.admit(
                evidenceId,
                p.workspaceIds(),
                p.userId(),
                request.sourceVersion(),
                request.brandVersion(),
                request.workspaceVersion(),
                request.contentChecksum()));
  }

  @GetMapping("/{admissionId}/snapshot")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<EvidenceAdmissionSnapshot> snapshot(
      Authentication authentication,
      @PathVariable UUID evidenceId,
      @PathVariable UUID admissionId) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.snapshot(evidenceId, admissionId, p.workspaceIds(), p.userId()));
  }

  @PostMapping("/{admissionId}/withdrawals")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<EvidenceWithdrawalDetails> withdraw(
      Authentication authentication,
      @PathVariable UUID evidenceId,
      @PathVariable UUID admissionId) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(reviews.withdraw(evidenceId, admissionId, p.workspaceIds(), p.userId()));
  }
}
