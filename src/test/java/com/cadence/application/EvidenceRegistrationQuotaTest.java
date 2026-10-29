package com.cadence.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "cadence.evidence.registration.max-workspace-evidence=2",
      "cadence.evidence.registration.max-workspace-registrations-per-hour=1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvidenceRegistrationQuotaTest extends EvidenceRegistrationQuotaContract {}
