package com.cadence.domain;

import java.time.Instant;
import java.util.*;

public record CsvExportRequest(
    Instant from, Instant until, String timezone, List<Selection> selections)
    implements StrictPlaybookValue {
  public record Selection(UUID postId, UUID revisionId, long revisionVersion, int scheduleVersion)
      implements StrictPlaybookValue {
    public Selection {
      if (postId == null
          || revisionId == null
          || revisionVersion < 0
          || scheduleVersion < 1
          || scheduleVersion > 999) throw new IllegalArgumentException("Invalid export selection");
    }
  }

  public CsvExportRequest {
    CalendarTime.interval(from, until, timezone);
    if (from.getNano() % 1000 != 0
        || until.getNano() % 1000 != 0
        || selections == null
        || selections.isEmpty()
        || selections.size() > 84
        || selections.stream().anyMatch(Objects::isNull)
        || selections.stream().map(Selection::postId).distinct().count() != selections.size())
      throw new IllegalArgumentException("Invalid bounded export request");
    selections =
        selections.stream().sorted(Comparator.comparing(s -> s.postId().toString())).toList();
  }

  public String digest() {
    var b = new StringBuilder("csv-export-request-v1:");
    CsvDocument.part(b, from.toString());
    CsvDocument.part(b, until.toString());
    CsvDocument.part(b, timezone);
    selections.forEach(
        s -> {
          CsvDocument.part(b, s.postId().toString());
          CsvDocument.part(b, s.revisionId().toString());
          CsvDocument.part(b, Long.toString(s.revisionVersion()));
          CsvDocument.part(b, Integer.toString(s.scheduleVersion()));
        });
    return BrandAnalysisAttempt.checksum(b.toString());
  }
}
