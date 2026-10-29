# Encrypted PDF security fixture

`encrypted-aes256.pdf` is a single blank 72 × 72 point page, generated with
bundled `pypdf` 6.10.0 and `cryptography` 50.0.1 using
`PdfWriter.encrypt(random_password, algorithm="AES-256")`. Its random password
was discarded after verifying successful decryption and a one-page result.
It contains no user data, credentials, malware or external content.

The Java integration tests use the binary fixture directly; these Python packages
are not application or CI runtime dependencies. The purpose is to prove that
the configured real ClamAV daemon refuses to classify password-protected bytes
as inspected CLEAN evidence (`AlertEncrypted yes`).

See [pypdf encryption documentation](https://pypdf.readthedocs.io/en/6.10.0/user/encryption-decryption.html).
