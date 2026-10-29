package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RecommendationProposalTest {
  private List<UUID> citations(int n) {
    return java.util.stream.IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
  }

  @Test
  void unknownDuplicateAndOversizedCitationsDenied() {
    for (int n : new int[] {0, 9, 101})
      assertThatThrownBy(() -> new RecommendationProposal("a", "b", 5, citations(n)))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new RecommendationProposal("a", "b", 5, Collections.nCopies(10, UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arbitraryActionsAndSamePillarDenied() {
    for (int n : new int[] {-1, 0, 4, 6, 100, Integer.MAX_VALUE})
      assertThatThrownBy(() -> new RecommendationProposal("a", "b", n, citations(10)))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RecommendationProposal("a", "a", 5, citations(10)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
