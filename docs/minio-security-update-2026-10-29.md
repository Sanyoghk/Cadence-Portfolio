# MinIO fixture dependency update — 2026-10-29

After the gateway fix passed, the complete security workflow reached MinIO and rejected
five HIGH, six UNKNOWN and two MEDIUM raw findings across the server and client binaries.
Two UNKNOWN entries were the previously reviewed absent OpenPGP packages; all other
findings remained blocking. No scanner suppression or gate reduction was introduced.

Both binaries now build with official Go 1.27.3-alpine pinned to its OCI index digest.
The module resolver updated `klauspost/compress` to 1.18.7, `golang.org/x/net` to 0.62.0
and the server's `go-jose/v4` to 4.1.6, together with their required `golang.org/x/*`
module upgrades. Updated `go.mod` and `go.sum` files retain exact versions and checksums.
The source archives and their SHA256 checks remain unchanged.

These updates address the reported compression DoS (CVE-2026-63209), certificate-validation
issue (CVE-2026-111943), JWK parsing DoS and parsing panics (CVE-2026-116585/6/7), and
HTML-processing findings (CVE-2026-78661, CVE-2026-97045). Applicability was not dismissed
merely because this is a local fixture.

References: [Go release history](https://go.dev/doc/devel/release),
[compress release](https://github.com/klauspost/compress/releases/tag/v1.18.7),
[go-jose release](https://github.com/go-jose/go-jose/releases/tag/v4.1.6).

Changed files: `docker/minio/Dockerfile`, `docker/minio/server.go.mod`,
`docker/minio/server.go.sum`, `docker/minio/client.go.mod`, `docker/minio/client.go.sum`,
and this report. Both the public copy and private audit branch receive the correction.
Application Java/TypeScript code and external API contracts are unchanged.

Local preparation uses checksum-verified official Go 1.27.3, `go get` for the exact module
versions, `go mod download all` and `go mod verify`, with the official module proxy and
checksum database. Actual Linux readonly compilation, binary package inventory, image
scan and application integration checks run in GitHub Actions. Their current result is
available on the exact commit's Actions page; this document does not predeclare success.

The MinIO community fixture remains for local/integration testing only. A passing scan
does not qualify the archived community server or its integrations for production.
