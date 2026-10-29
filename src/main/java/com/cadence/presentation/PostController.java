package com.cadence.presentation;

import com.cadence.application.results.PostDetails;
import com.cadence.application.usecases.PostLifecycleUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.*;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("denyAll()")
public class PostController {
  private final PostLifecycleUseCase posts;

  public PostController(PostLifecycleUseCase posts) {
    this.posts = posts;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  private URI revisionLocation(PostDetails d) {
    return URI.create("/api/v1/posts/" + d.id() + "/revisions/" + d.revisionId());
  }

  @PostMapping("/brands/{brandId}/posts")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<PostDetails> create(
      Authentication a, @PathVariable UUID brandId, @Valid @RequestBody CreatePostRequest r) {
    var d =
        posts.create(
            brandId,
            scope(a),
            r.strategyId(),
            r.expectedStrategyVersion(),
            r.platform(),
            r.content().content());
    return ResponseEntity.created(URI.create("/api/v1/posts/" + d.id())).body(d);
  }

  @GetMapping("/brands/{brandId}/posts")
  @PreAuthorize("hasRole('OWNER')")
  public PageResponse<PostDetails> list(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        posts.list(
            brandId, scope(a), ApiPagination.request(page, size, "createdAt", "desc", "createdAt")),
        d -> d);
  }

  @GetMapping("/posts/{postId}")
  @PreAuthorize("hasRole('OWNER')")
  public PostDetails read(Authentication a, @PathVariable UUID postId) {
    return posts.read(postId, scope(a));
  }

  @GetMapping("/posts/{postId}/revisions")
  @PreAuthorize("hasRole('OWNER')")
  public PageResponse<PostDetails> history(
      Authentication a,
      @PathVariable UUID postId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        posts.history(
            postId,
            scope(a),
            ApiPagination.request(page, size, "revisionNumber", "desc", "revisionNumber")),
        d -> d);
  }

  @GetMapping("/posts/{postId}/revisions/{revisionId}")
  @PreAuthorize("hasRole('OWNER')")
  public PostDetails revision(
      Authentication a, @PathVariable UUID postId, @PathVariable UUID revisionId) {
    return posts.revision(postId, revisionId, scope(a));
  }

  @PatchMapping("/posts/{postId}/revisions/{revisionId}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<PostDetails> edit(
      Authentication a,
      @PathVariable UUID postId,
      @PathVariable UUID revisionId,
      @Valid @RequestBody EditPostRequest r) {
    var edited =
        posts.edit(postId, revisionId, scope(a), r.expectedVersion(), r.content().content());
    return edited.created()
        ? ResponseEntity.created(revisionLocation(edited.details())).body(edited.details())
        : ResponseEntity.ok(edited.details());
  }

  @PostMapping("/posts/{postId}/revisions/{revisionId}/approval")
  @PreAuthorize("hasRole('OWNER')")
  public PostDetails approve(
      Authentication a,
      @PathVariable UUID postId,
      @PathVariable UUID revisionId,
      @Valid @RequestBody PostApprovalRequest r) {
    return posts.approve(postId, revisionId, scope(a), r.expectedVersion());
  }
}
