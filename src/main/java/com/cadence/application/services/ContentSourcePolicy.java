package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ContentSourcePolicy {
  private final StrategyRepository strategies;
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;

  public ContentSourcePolicy(
      StrategyRepository strategies,
      ContentPostRepository posts,
      PostRevisionRepository revisions) {
    this.strategies = strategies;
    this.posts = posts;
    this.revisions = revisions;
  }

  public ContentCapture capture(
      Brand brand,
      StrategyGenerationCommand context,
      ApprovedIdentitySource identity,
      ContentGenerationRequest request,
      ContentCapture frozen) {
    var source =
        strategies
            .findLockedByIdAndBrandId(request.strategyId(), brand.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Strategy not found"));
    if (!source.isReadyContentSource() || source.getLockVersion() != request.strategyVersion())
      throw new IllegalStateException("Content Strategy unavailable");
    source.validateContent();
    var origin = source;
    for (int i = 0;
        origin.getGeneration() == null || origin.getGeneration().getIdentitySource() == null;
        i++) {
      if (i >= 1000 || origin.getRevisedFromId() == null)
        throw new IllegalStateException("Strategy Identity lineage required");
      var prior = strategies.findById(origin.getRevisedFromId()).orElseThrow();
      if (!prior.getBrand().getId().equals(brand.getId())
          || prior.getVersion() >= origin.getVersion())
        throw new IllegalStateException("Invalid Strategy lineage");
      origin = prior;
    }
    var inherited = origin.getGeneration().getIdentitySource();
    if (!inherited.identityId().equals(identity.identityId())
        || inherited.version() != identity.version()
        || inherited.lockVersion() != identity.lockVersion()
        || !inherited.checksum().equals(identity.checksum())
        || !inherited.approvedBy().equals(identity.approvedBy())
        || !inherited.approvedAt().equals(identity.approvedAt()))
      throw new IllegalStateException("Strategy Identity changed");
    PostContent baseline = null;
    if (request.postId() != null) {
      var post =
          posts
              .findLocked(request.postId(), brand.getId())
              .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
      post.requireCurrentSource();
      var r = revisions.findTopByPostIdOrderByRevisionNumberDesc(post.getId()).orElseThrow();
      if (!post.getStrategy().getId().equals(source.getId())
          || !r.getId().equals(request.revisionId())
          || r.getLockVersion() != request.revisionVersion()
          || !Set.of(PostRevision.State.DRAFT, PostRevision.State.APPROVED).contains(r.getStatus())
          || r.getRevisionNumber() >= 1000
          || (post.getOutlineSlotId() != null
              && !post.getOutlineSlotId().equals(request.slotIds().getFirst())))
        throw new IllegalStateException("Regeneration source changed");
      var slot =
          source.getPlaybook().fourWeekOutline().stream()
              .flatMap(w -> w.slots().stream())
              .filter(s -> s.slotId().equals(request.slotIds().getFirst()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown slot"));
      if (slot.channel() != post.getPlatform())
        throw new IllegalArgumentException("Regeneration platform mismatch");
      baseline = r.content();
    }
    if (request.postId() == null
        && posts.countByBrandId(brand.getId()) + request.slotIds().size() > 10000)
      throw new IllegalStateException("Post capacity reached");
    var command =
        new ContentGenerationCommand(
            context.generationId(),
            context.brandName(),
            context.industry(),
            context.signature(),
            StrategyContent.from(source),
            source.getPlaybook(),
            request.slotIds(),
            request.field(),
            baseline);
    var capture =
        new ContentCapture(
            request,
            source.getId(),
            source.getVersion(),
            source.getLockVersion(),
            source.getApprovedBy(),
            source.getApprovedAt(),
            BrandAnalysisAttempt.checksum(
                source.getPlaybook().canonical(StrategyContent.from(source))),
            origin.getId(),
            context,
            command);
    if (frozen != null && !frozen.equals(capture))
      throw new IllegalStateException("Captured Content source changed");
    return capture;
  }
}
