# Security and publication boundaries

Cadence is under development. Repository availability is not production qualification.
Live provider activation, account lifecycle, deployment and infrastructure gates remain
separate from source review; see [the backend security contract](docs/backend-security-gate.md).

Do not put credentials, user data, private logs or exploit details in public issues or PRs.
If GitHub's **Security → Report a vulnerability** option is enabled, use that private channel.
If it is unavailable, request a private reporting channel without posting sensitive details.

Before changing repository visibility, review **every branch and reachable commit**, author
metadata, pull requests, issues, Actions logs/artifacts and bundled media. Deleting a file
from the latest commit does not remove its historical contents. `.gitignore` does not remove
already tracked files. A `.mailmap` does not erase original author addresses from Git objects.

Run the redacted secret and privacy checks documented in
[the publication audit](docs/publication-audit-2026-10-28.md). These detectors are heuristic;
a passing scan is not proof that all personal data or vulnerabilities are absent.

If a real credential is ever committed, revoke/rotate it at its provider first. Then assess
history cleanup, cached PR views, clones and artifacts. Never validate a suspected leaked
key by making a paid or production API call.

The 2026-10-28 audit applies to the original private development repository, which remains
private. This separate portfolio repository starts with a sanitized source snapshot and a
new Git history. See [publication notes](docs/publication-release-2026-10-29.md).
Never merge private development history into this repository; review each future export.
