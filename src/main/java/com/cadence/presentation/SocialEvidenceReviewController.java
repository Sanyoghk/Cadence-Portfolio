package com.cadence.presentation;

import com.cadence.application.results.SocialAdmissionDetails;
import com.cadence.application.results.SocialAdmissionSnapshot;
import com.cadence.application.results.SocialReviewPreview;
import com.cadence.application.usecases.ReviewSocialEvidenceUseCase;
import com.cadence.presentation.dto.AdmitSocialEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence/{evidenceId}/social-review")
@PreAuthorize("denyAll()")
public class SocialEvidenceReviewController {
  private final ReviewSocialEvidenceUseCase reviews;

  public SocialEvidenceReviewController(ReviewSocialEvidenceUseCase reviews) {
    this.reviews = reviews;
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<SocialReviewPreview> preview(
      Authentication auth, @PathVariable UUID evidenceId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.preview(evidenceId, p.workspaceIds(), p.userId()));
  }

  @PostMapping("/admissions")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<SocialAdmissionDetails> admit(
      Authentication auth,
      @PathVariable UUID evidenceId,
      @Valid @RequestBody AdmitSocialEvidenceRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.status(201)
        .cacheControl(CacheControl.noStore())
        .body(reviews.admit(evidenceId, p.workspaceIds(), p.userId(), request.selectors()));
  }

  @GetMapping("/admissions/{admissionId}/snapshot")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<SocialAdmissionSnapshot> snapshot(
      Authentication auth, @PathVariable UUID evidenceId, @PathVariable UUID admissionId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviews.snapshot(evidenceId, admissionId, p.workspaceIds(), p.userId()));
  }
}
