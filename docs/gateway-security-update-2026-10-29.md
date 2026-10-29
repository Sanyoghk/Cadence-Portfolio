# Gateway security update — 2026-10-29

The first public source/container CI run rejected the local gateway image: Trivy reported
three HIGH and three MEDIUM findings in nginx 1.30.5-r1. The HIGH records were
CVE-2026-15176, CVE-2026-93479 and CVE-2026-97205. The scanner gate was not weakened.

The gateway now uses the official nginx 1.30.6-alpine image pinned to its verified OCI
index digest. [NGINX advisories](https://nginx.org/en/security_advisories.html) identify
1.30.6 as fixed for the HIGH records. Configuration does not enable alias/rewrite, AIO,
HTTP/3 or TLS sessions; nevertheless, the vulnerable package is replaced rather than ignored.

CI additionally runs `nginx -t` with the checked-in HTTP/TCP configuration, UID 10001,
read-only filesystem, no capabilities, no network and bounded resources. Static host
entries permit upstream name validation without network access. This verifies configuration
compatibility; it is not an end-to-end proxy test. Existing Trivy scanning checks the built
image and rejects HIGH/CRITICAL or unreviewed UNKNOWN findings.

Changed files: `docker/local-gateway/Dockerfile`, `.github/workflows/source-security.yml`,
and this report. Java/TypeScript application source, API and database contracts are unchanged.
The private source audit branch receives the same Dockerfile/workflow correction; its Git
history is not imported into this public repository.

Local validation: `python -m unittest discover -s scripts -p 'test_*.py'` passed all 26 tests;
`git diff --check` passed. Docker was unavailable locally, so the actual image build,
configuration check and vulnerability rescan are performed by GitHub Actions on this commit.
Their result must be read from the corresponding run, not inferred from this document.

The SAST warning in `PinnedHttpTransport` was also inspected: the bare socket connects to a
validated public IP; HTTPS wraps it in an `SSLSocket`, enables HTTPS hostname verification
and performs a TLS handshake. Plain HTTP is supported for public evidence ingestion;
the generated request contains no caller cookies, bearer tokens or credentials. This
warning is retained in scanner artifacts. HTTP evidence remains susceptible to tampering
in transit and must not be treated as authenticated or used to send confidential data.

The repository remains a development portfolio, not a qualified production deployment.
Subsequent public commits extend the independent root history; none imports private commits.
