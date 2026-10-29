package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.PostDetails;
import com.cadence.application.services.MediaVerification;
import com.cadence.application.services.PostMediaPolicy;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class PostLifecycleUseCase {
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;
  private final StrategyRepository strategies;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final Clock clock;
  private final MediaVerification mediaVerification;
  private final PostMediaPolicy mediaPolicy;

  public PostLifecycleUseCase(
      ContentPostRepository posts,
      PostRevisionRepository revisions,
      StrategyRepository strategies,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      Clock clock,
      MediaVerification mediaVerification,
      PostMediaPolicy mediaPolicy) {
    this.posts = posts;
    this.revisions = revisions;
    this.strategies = strategies;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.clock = clock;
    this.mediaVerification = mediaVerification;
    this.mediaPolicy = mediaPolicy;
  }

  public record Edited(PostDetails details, boolean created) {}

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public PostDetails create(
      UUID brandId,
      Set<UUID> scope,
      UUID strategyId,
      long expectedStrategyVersion,
      PlaybookContent.Channel platform,
      PostContent content) {
    UUID workspaceId =
        brands.findWorkspaceIdIn(brandId, scope).orElseThrow(PostLifecycleUseCase::missing);
    UUID actor = lockActor(workspaceId);
    lockParents(brandId, scope);
    Strategy source =
        strategies
            .findLockedByIdAndBrandId(strategyId, brandId)
            .orElseThrow(PostLifecycleUseCase::missing);
    if (expectedStrategyVersion < 0 || source.getLockVersion() != expectedStrategyVersion)
      throw new IllegalStateException("Strategy changed");
    if (posts.countByBrandId(brandId) >= 10000)
      throw new IllegalStateException("Post count limit reached");
    var post = new ContentPost(source, platform, actor, clock.instant());
    var revision = new PostRevision(post, content, actor, clock.instant());
    posts.saveAndFlush(post);
    revisions.saveAndFlush(revision);
    return PostDetails.from(revision, true);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Edited edit(
      UUID postId, UUID revisionId, Set<UUID> scope, long expectedVersion, PostContent content) {
    return mediaVerification.execute(
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          content.validateFor(post.getPlatform());
          return mediaPolicy.capture(post, content.format(), current.getMediaAssetIds());
        },
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          var actor = principal().userId();
          boolean branch = current.getStatus() == PostRevision.State.APPROVED;
          if (branch) current = current.branch(content, actor, clock.instant());
          else current.replace(content, actor, clock.instant());
          revisions.saveAndFlush(current);
          return new Edited(PostDetails.from(current, true), branch);
        });
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PostDetails approve(UUID postId, UUID revisionId, Set<UUID> scope, long expectedVersion) {
    return mediaVerification.execute(
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          return mediaPolicy.capture(post, current.getFormat(), current.getMediaAssetIds());
        },
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          current.approve(principal().userId(), clock.instant());
          revisions.flush();
          return PostDetails.from(current, true);
        });
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Edited replaceMedia(
      UUID postId, UUID revisionId, Set<UUID> scope, long expectedVersion, List<UUID> ids) {
    return mediaVerification.execute(
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          return mediaPolicy.capture(post, current.getFormat(), ids);
        },
        () -> {
          var post = locked(postId, scope);
          var current = current(post, revisionId, expectedVersion);
          var actor = principal().userId();
          boolean branch = current.getStatus() == PostRevision.State.APPROVED;
          var now = clock.instant();
          if (branch) current = current.branch(current.content(), actor, now);
          current.replaceMedia(ids, actor, now);
          revisions.saveAndFlush(current);
          return new Edited(PostDetails.from(current, true), branch);
        });
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public PostDetails read(UUID id, Set<UUID> scope) {
    var post = visible(id, scope);
    return PostDetails.from(latest(post), true);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public PostDetails revision(UUID id, UUID revisionId, Set<UUID> scope) {
    var post = visible(id, scope);
    var revision =
        revisions.findByIdAndPostId(revisionId, id).orElseThrow(PostLifecycleUseCase::missing);
    return PostDetails.from(revision, revision.getId().equals(latest(post).getId()));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<PostDetails> list(UUID brandId, Set<UUID> scope, Pageable page) {
    validatePage(page, Set.of("createdAt", "id"));
    var brand =
        brands.findByIdAndWorkspaceIdIn(brandId, scope).orElseThrow(PostLifecycleUseCase::missing);
    lockActor(brand.getWorkspace().getId());
    return posts.findAllByBrandId(brandId, page).map(p -> PostDetails.from(latest(p), true));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<PostDetails> history(UUID id, Set<UUID> scope, Pageable page) {
    validatePage(page, Set.of("revisionNumber", "createdAt", "id"));
    var post = visible(id, scope);
    UUID latest = latest(post).getId();
    return revisions
        .findAllByPostId(id, page)
        .map(r -> PostDetails.from(r, r.getId().equals(latest)));
  }

  private ContentPost visible(UUID id, Set<UUID> scope) {
    var post =
        posts.findByIdAndBrandWorkspaceIdIn(id, scope).orElseThrow(PostLifecycleUseCase::missing);
    lockActor(post.getBrand().getWorkspace().getId());
    return post;
  }

  private ContentPost locked(UUID id, Set<UUID> scope) {
    UUID brandId = posts.findBrandIdIn(id, scope).orElseThrow(PostLifecycleUseCase::missing);
    lockActor(brands.findWorkspaceIdIn(brandId, scope).orElseThrow(PostLifecycleUseCase::missing));
    lockParents(brandId, scope);
    // Source precedes Post lock; strategy ID is immutable and read in the same transaction.
    UUID sourceId = posts.findById(id).orElseThrow().getStrategy().getId();
    strategies
        .findLockedByIdAndBrandId(sourceId, brandId)
        .orElseThrow(PostLifecycleUseCase::missing);
    var post = posts.findLocked(id, brandId).orElseThrow(PostLifecycleUseCase::missing);
    post.requireCurrentSource();
    return post;
  }

  private PostRevision current(ContentPost p, UUID revisionId, long expected) {
    var r = latest(p);
    if (!r.getId().equals(revisionId) || expected < 0 || r.getLockVersion() != expected)
      throw new IllegalStateException("Post revision changed");
    return r;
  }

  private PostRevision latest(ContentPost p) {
    return revisions
        .findTopByPostIdOrderByRevisionNumberDesc(p.getId())
        .orElseThrow(PostLifecycleUseCase::missing);
  }

  private void lockParents(UUID brandId, Set<UUID> scope) {
    var workspace =
        workspaces
            .findLockedByIdAndIdIn(brands.findWorkspaceIdIn(brandId, scope).orElseThrow(), scope)
            .orElseThrow();
    var brand = brands.findLockedWithWorkspaceIn(brandId, scope).orElseThrow();
    if (workspace.getStatus() != WorkspaceStatus.ACTIVE || brand.getStatus() != BrandStatus.ACTIVE)
      throw new IllegalStateException("Post parent archived");
  }

  private UUID lockActor(UUID workspace) {
    var actor =
        users
            .findLockedById(principal().userId())
            .orElseThrow(() -> new AccessDeniedException("Post access denied"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || !users.hasWorkspaceMembership(actor.getId(), workspace))
      throw new AccessDeniedException("Post access denied");
    return actor.getId();
  }

  private CadencePrincipal principal() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof CadencePrincipal p))
      throw new AccessDeniedException("Post access denied");
    return p;
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Post or source not found");
  }

  private void validatePage(Pageable p, Set<String> allowed) {
    if (p == null
        || p.isUnpaged()
        || p.getPageSize() > 100
        || p.getPageNumber() > 10000
        || p.getSort().stream().anyMatch(o -> !allowed.contains(o.getProperty())))
      throw new IllegalArgumentException("Invalid Post pagination");
  }
}
