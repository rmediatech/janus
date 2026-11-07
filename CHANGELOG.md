# 🏛️ Janus — Engineer-to-Engineer Changelog (`SOVEREIGN-CHANGELOG-E2E-01`)

All architectural enhancements, security invariants, and empirical verifications are documented below from engineers to engineers.

---

## `[v1.1.0]` — 2026-11-07

### 1. `[Security/Sideload]` APK Zip-Slip Traversal Defense, `classes.dex` Verification & Streaming SHA-256 Provenance (`SOVEREIGN-APK-ZIPSLIP-01`)
* **Root Cause Dissection**: Establishing structural superiority over `localsend/localsend`, `pairdrop`, and `kdeconnect`: a naive ZIP magic-byte + `AndroidManifest.xml` check can be bypassed by polyglot archives lacking executable Dalvik bytecode (`classes.dex`), Zip central-directory entry exhaustion bombs, or Zip-Slip archives containing `../../` traversal paths inside ZIP entry headers.
* **Architectural & Mathematical Resolution**:
  * Upgraded `ApkSideloadValidator.validateApkFile` to require both `AndroidManifest.xml` and `classes.dex`, enforce a hard `MAX_APK_ZIP_ENTRIES = 16,384` ceiling, and validate every ZIP entry through `isUnsafeZipEntryName` (rejecting `..` segments, leading `/` or `\`, and null bytes `\u0000`).
  * Engineered `ApkSideloadValidator.computeSha256Hex` and `JanusFileSystem.computeFileSha256` using streaming `8 KiB` chunked `MessageDigest("SHA-256")` evaluation for zero-heap-spike file integrity verification.
  * Hardened `JanusFileSystem.streamZipArchive` with `Files.isSymbolicLink` symlink-loop evasion and `MAX_STREAM_ARCHIVE_ENTRIES = 10,000` bounded streaming.
* **Empirical Verification**: Verified in `AdversarialJanusSecurityTest::testApkZipSlipTraversalAndMissingDexRejected` and `ApkSideloadValidatorTest` (36/36 unit & adversarial tests green).

---

## `[v1.0.0]` — 2026-11-06
* Initial flagship release of **Janus Sovereign Workstation & Mobile Bridge** featuring NanoHTTPD + WebSocket bidirectional bridge, QR pairing, Scoped Storage ZIP streaming, live Logcat streaming, and workstation Ed25519 identity anchoring across DHCP lease drift.
