package com.cadence.presentation;

import com.cadence.application.results.*;
import com.cadence.application.usecases.*;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.*;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class MediaController {
  private final MediaAssetUseCase media;
  private final PostLifecycleUseCase posts;

  public MediaController(MediaAssetUseCase media, PostLifecycleUseCase posts) {
    this.media = media;
    this.posts = posts;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping("/brands/{brandId}/media-assets")
  public ResponseEntity<MediaDetails> create(
      Authentication a, @PathVariable UUID brandId, @Valid @RequestBody CreateMediaRequest r) {
    var d = media.create(brandId, scope(a), r.fixture());
    return ResponseEntity.created(URI.create("/api/v1/media-assets/" + d.id()))
        .cacheControl(CacheControl.noStore())
        .body(d);
  }

  @GetMapping("/brands/{brandId}/media-assets")
  public PageResponse<MediaDetails> list(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        media.list(
            brandId, scope(a), ApiPagination.request(page, size, "createdAt", "desc", "createdAt")),
        d -> d);
  }

  @GetMapping("/media-assets/{assetId}")
  public MediaDetails read(Authentication a, @PathVariable UUID assetId) {
    return media.read(assetId, scope(a));
  }

  @PostMapping("/media-assets/{assetId}/review")
  public MediaDetails review(
      Authentication a, @PathVariable UUID assetId, @Valid @RequestBody ReviewMediaRequest r) {
    return media.review(assetId, scope(a), r.expectedVersion(), r.approved());
  }

  @PostMapping("/media-assets/{assetId}/archive")
  public MediaDetails archive(
      Authentication a, @PathVariable UUID assetId, @Valid @RequestBody PostApprovalRequest r) {
    return media.archive(assetId, scope(a), r.expectedVersion());
  }

  @GetMapping("/media-assets/{assetId}/preview")
  public ResponseEntity<byte[]> preview(
      Authentication a,
      @PathVariable UUID assetId,
      @RequestHeader(value = "Range", required = false) String range) {
    return delivery(a, assetId, true, range);
  }

  @GetMapping("/media-assets/{assetId}/bytes")
  public ResponseEntity<byte[]> bytes(
      Authentication a,
      @PathVariable UUID assetId,
      @RequestHeader(value = "Range", required = false) String range) {
    return delivery(a, assetId, false, range);
  }

  private ResponseEntity<byte[]> delivery(
      Authentication a, UUID id, boolean preview, String range) {
    if (range != null) throw new IllegalArgumentException("Media Range requests unsupported");
    var result = media.delivery(id, scope(a), preview);
    var d = result.asset();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .contentType(MediaType.parseMediaType(d.contentType()))
        .header(
            "Content-Disposition",
            "attachment; filename=\"demo-" + d.id() + "." + d.format().extension() + "\"")
        .header("X-Content-Type-Options", "nosniff")
        .header("Accept-Ranges", "none")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("X-Cadence-Media-Origin", d.origin())
        .header("X-Cadence-Media-Moderation", d.moderation().name())
        .contentLength(d.byteSize())
        .body(result.bytes());
  }

  @PutMapping("/posts/{postId}/revisions/{revisionId}/media")
  public ResponseEntity<PostDetails> replace(
      Authentication a,
      @PathVariable UUID postId,
      @PathVariable UUID revisionId,
      @Valid @RequestBody ReplacePostMediaRequest r) {
    var e = posts.replaceMedia(postId, revisionId, scope(a), r.expectedVersion(), r.assetIds());
    return e.created()
        ? ResponseEntity.created(
                URI.create("/api/v1/posts/" + postId + "/revisions/" + e.details().revisionId()))
            .body(e.details())
        : ResponseEntity.ok(e.details());
  }
}
