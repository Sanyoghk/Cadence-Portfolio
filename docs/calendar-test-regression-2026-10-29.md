# Calendar test date regression — 2026-10-29

The initial full backend CI ran 2311 tests with zero assertion failures and two errors:
the same DST contract failed on H2 and PostgreSQL. On 2026-10-29 the next London autumn
fold falls outside the application's unchanged 366-day scheduling horizon. Annual
transitions can be 371 days apart; choosing the next London fold is not always a valid
future scheduling fixture. The domain correctly rejected the request.

`CalendarDstFixtures` selects the nearest real future overlap from London and Sydney,
whose daylight-saving seasons are in opposite hemispheres. `CalendarApiContract` still
persists both valid offsets, asserts their exact UTC instants and rejects a nonexistent
local time. The production clock, 366-day bound, authorization and database constraints
are unchanged. No test was removed and no expected rejection or assertion was relaxed.

`CalendarDstFixturesTest` adds six date-boundary scenarios and reproduces the original
late-October London rejection. It checks actual timezone rules and calls the original
`CalendarTime.requireFuture` for both offsets. H2 and PostgreSQL continue sharing the
same API contract.

Changed files: `src/test/java/com/cadence/support/CalendarApiContract.java`,
`src/test/java/com/cadence/support/CalendarDstFixtures.java`,
`src/test/java/com/cadence/support/CalendarDstFixturesTest.java`, and this report.

Executed locally:

```text
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply -Dtest=CalendarDstFixturesTest,CalendarApiIntegrationTest,CalendarTimeTest test
```

Result: BUILD SUCCESS; 46 tests, zero failures/errors/skips (31 API integration, 8 domain,
7 fixture regression). The Spring test application started successfully. Existing
Jackson/S3 API deprecation warnings remain. Full PostgreSQL/container verification runs
in GitHub Actions on the new source SHA; this local H2 result does not substitute for it.
