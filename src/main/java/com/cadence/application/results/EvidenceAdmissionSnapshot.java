package com.cadence.application.results;

/** Current at capture time only; consumers must recheck before publishing after external I/O. */
public record EvidenceAdmissionSnapshot(EvidenceAdmissionDetails admission, String text) {}
