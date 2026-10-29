package com.cadence.presentation;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Core API readiness; optional providers have separate feature availability and activation gates.
 */
@RestController
public class ReadinessController {
  private final DataSource database;

  public ReadinessController(DataSource database) {
    this.database = database;
  }

  public record Probe(String status) {}

  @GetMapping("/api/v1/health/ready")
  public ResponseEntity<?> ready() {
    try (var connection = database.getConnection();
        var statement = connection.createStatement()) {
      statement.setQueryTimeout(1);
      try (var result = statement.executeQuery("SELECT 1")) {
        if (result.next() && result.getInt(1) == 1) return ResponseEntity.ok(new Probe("READY"));
      }
    } catch (SQLException unavailable) {
      // A fixed status never reveals credentials, connection URLs, drivers or SQL diagnostics.
    }
    return ResponseEntity.status(503)
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(com.cadence.http.ApiProblems.status(503));
  }
}
