package com.cadence.application.services;

import com.cadence.domain.CsvDocument;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Pure bounded CPU work; separately testable crash/lease boundary. */
@Component
public class CsvExportRenderer {
  public byte[] render(UUID id, List<List<String>> rows) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("CSV rendering requires nontransactional caller");
    var output = new ArrayList<List<String>>();
    for (var row : rows) {
      var copy = new ArrayList<>(row);
      copy.set(2, id.toString());
      output.add(List.copyOf(copy));
    }
    return CsvDocument.render(output);
  }
}
