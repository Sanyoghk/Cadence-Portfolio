package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Called only inside the coordinator's fenced publication transaction. */
@Service
public class ContentPublication {
  private final ContentGenerationBatchRepository batches;
  private final StrategyRepository strategies;
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;

  public ContentPublication(
      ContentGenerationBatchRepository batches,
      StrategyRepository strategies,
      ContentPostRepository posts,
      PostRevisionRepository revisions) {
    this.batches = batches;
    this.strategies = strategies;
    this.posts = posts;
    this.revisions = revisions;
  }

  public UUID publish(
      AiGenerationOperation operation,
      int attempt,
      ContentCapture capture,
      ContentGenerationResult output,
      String original,
      Instant now) {
    output.validateFor(capture.command());
    var batch =
        batches.saveAndFlush(
            new ContentGenerationBatch(
                operation.getId(), attempt, BrandAnalysisAttempt.checksum(original)));
    var request = capture.request();
    var source = strategies.findById(capture.strategyId()).orElseThrow();
    for (var slot : capture.command().selected()) {
      var item =
          output.items().stream()
              .filter(i -> i.slotId().equals(slot.slotId()))
              .findFirst()
              .orElseThrow();
      PostRevision prior = null;
      ContentPost post;
      if (request.postId() == null) {
        post = new ContentPost(source, slot.channel(), operation.getActorId(), now);
        post.bindOutlineSlot(slot.slotId());
        posts.saveAndFlush(post);
      } else {
        post = posts.findById(request.postId()).orElseThrow();
        prior = revisions.findByIdAndPostId(request.revisionId(), post.getId()).orElseThrow();
      }
      var content = request.merge(capture.command().baseline(), item.content());
      content.validateFor(slot.channel());
      var revision =
          PostRevision.generated(
              post, content, operation.getActorId(), now, batch.getId(), slot.slotId(), prior);
      if (prior != null) revisions.flush();
      revisions.saveAndFlush(revision);
    }
    return batch.getId();
  }

  public List<PostRevision> revisions(UUID batch) {
    return revisions.findAllByGenerationBatchIdOrderByGenerationSlotId(batch);
  }
}
