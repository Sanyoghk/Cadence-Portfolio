package com.cadence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CadenceController {

  @GetMapping("/api/v1/health")
  public String health() {
    return "Cadence backend is running";
  }
}
