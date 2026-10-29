package com.cadence.presentation;

import com.cadence.application.results.PdfAdmissionDetails;
import com.cadence.application.results.PdfAdmissionSnapshot;
import com.cadence.application.results.PdfReviewPreview;
import com.cadence.application.usecases.ReviewPdfEvidenceUseCase;
import com.cadence.presentation.dto.AdmitPdfEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/{evidenceId}/document-review")
@PreAuthorize("denyAll()")
public class PdfEvidenceReviewController {
  private final ReviewPdfEvidenceUseCase reviews;

  public PdfEvidenceReviewController(ReviewPdfEvidenceUseCase reviews) {
    this.reviews = reviews;
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<PdfReviewPreview> preview(
      Authentication auth, @PathVariable UUID evidenceId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.execute(evidenceId, p.workspaceIds(), p.userId()));
  }

  @PostMapping("/admissions")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<PdfAdmissionDetails> admit(
      Authentication auth,
      @PathVariable UUID evidenceId,
      @Valid @RequestBody AdmitPdfEvidenceRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(reviews.admit(evidenceId, p.workspaceIds(), p.userId(), request.selectors()));
  }

  @GetMapping("/admissions/{admissionId}/snapshot")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<PdfAdmissionSnapshot> snapshot(
      Authentication auth, @PathVariable UUID evidenceId, @PathVariable UUID admissionId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.snapshot(evidenceId, admissionId, p.workspaceIds(), p.userId()));
  }
}
