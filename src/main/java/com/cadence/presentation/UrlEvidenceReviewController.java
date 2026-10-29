package com.cadence.presentation;

import com.cadence.application.results.UrlAdmissionDetails;
import com.cadence.application.results.UrlAdmissionSnapshot;
import com.cadence.application.results.UrlReviewPreview;
import com.cadence.application.usecases.ReviewUrlEvidenceUseCase;
import com.cadence.presentation.dto.AdmitUrlEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/{evidenceId}/url-review")
@PreAuthorize("denyAll()")
public class UrlEvidenceReviewController {
  private final ReviewUrlEvidenceUseCase reviews;

  public UrlEvidenceReviewController(ReviewUrlEvidenceUseCase reviews) {
    this.reviews = reviews;
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<UrlReviewPreview> preview(
      Authentication auth, @PathVariable UUID evidenceId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.execute(evidenceId, p.workspaceIds(), p.userId()));
  }

  @PostMapping("/admissions")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<UrlAdmissionDetails> admit(
      Authentication auth,
      @PathVariable UUID evidenceId,
      @Valid @RequestBody AdmitUrlEvidenceRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(reviews.admit(evidenceId, p.workspaceIds(), p.userId(), request.selectors()));
  }

  @GetMapping("/admissions/{admissionId}/snapshot")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<UrlAdmissionSnapshot> snapshot(
      Authentication auth, @PathVariable UUID evidenceId, @PathVariable UUID admissionId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.snapshot(evidenceId, admissionId, p.workspaceIds(), p.userId()));
  }
}
