# 🏛️ Janus — Sovereign Workstation & Mobile Bridge

<div align="center">

[![Download Signed APK](https://img.shields.io/badge/Download-Signed%20APK%20(v1.0.0)-00F5FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/rmediatech/janus/releases/download/v1.0.0/janus-v1.0.0.apk)
[![RMediaTech Official](https://img.shields.io/badge/RMediaTech-Official%20Fleet-FFB300?style=for-the-badge&logo=shield&logoColor=black)](https://rmediatech.com)
[![Zero Cloud](https://img.shields.io/badge/Zero--Cloud-100%25%20Local-10B981?style=for-the-badge)](https://rmediatech.com)

</div>

> *"In ancient Rome, **Janus** was the guardian of doors, gates, transitions, and bridges—depicted with two faces looking simultaneously forward and backward. One face looking toward the desktop workstation; one face looking toward the mobile device."*

**Janus** is an ultra-high-performance, zero-cloud, bidirectional workstation bridge for Android and Desktop (Linux / macOS / Windows). It turns your Android phone into an autonomous wireless developer workstation and file server—accessible instantly from any desktop web browser with zero cables, zero third-party cloud relays, and zero subscriptions.

---

## ⚡ Core Developer Capabilities

* **🚀 Drag-and-Drop Remote APK Sideloading**:
  Drop any compiled `.apk` into the Janus web dashboard to stream it directly to your phone and trigger the native Android Package Installer with zero USB tethering.
* **📋 Real-Time Bi-Directional Clipboard Bridge**:
  Copy terminal commands, git commit hashes, JWT tokens, or URLs on your workstation, and they appear instantly on your phone's clipboard (and vice-versa).
* **📜 Wireless Live Logcat Streamer**:
  Inspect live, colorized, tag-filtered Android system and app logs directly in your browser without ever running `adb logcat`.
* **📁 Scoped Storage & Streamed Zip Engine**:
  Browse, search, upload, and delete files on your phone via modern Scoped Storage. Download full directories streamed as `.zip` archives on the fly with zero temporary memory buffering.
* **📷 Instant 1-Second Webcam / Camera QR Pairing**:
  Point your laptop webcam or phone camera at the on-screen QR code for instant cryptographic authentication without typing IP addresses or passwords.
* **🛡️ OJP Architecture & TDD++++++ Invariant**:
  Strict One-Job-Principle boundaries with dedicated adversarial test suites defending against path traversal, formula injection, null-byte truncations, and terminal poisoning.

---

## 🏗️ Architecture & Module Registry

Adhering strictly to **One-Job-Principle (OJP)** modularity:

| Module / Component | Sovereign Role |
| :--- | :--- |
| **`security/PathTraversalShield`** | Jail boundary enforcer neutralising `../`, null bytes, symlinks, and formula injections. |
| **`security/JanusAuthToken`** | 256-bit session secret minting, friendly PIN pairing codes, and anti-brute-force rate limiting. |
| **`explorer/JanusFileSystem`** | Scoped storage traversal, file metadata extraction, and zero-buffering streamed `.zip` generator. |
| **`clipboard/JanusClipboardBridge`** | In-memory ring-buffer for bi-directional clipboard sync with ANSI terminal escape code scrubbing. |
| **`installer/ApkSideloadValidator`** | Binary APK zip magic header validation (`PK\x03\x04`), manifest verification, and Intent dispatcher. |
| **`devtools/JanusLogcatStreamer`** | Threaded Android logcat capture with real-time WebSocket broadcast and tag filtering. |
| **`bridge/JanusHttpServer`** | Embedded NanoWSD server delivering the Single-Page Web Workstation, REST APIs, and WebSockets. |
| **`bridge/JanusBridgeService`** | Android Foreground Service maintaining Wi-Fi high-performance locks, mDNS broadcast, and sticky notifications. |
| **`ui/QrCodeGenerator`** | Pure Java ZXing bitmap renderer generating sharp pairing QR codes in Jetpack Compose. |
| **`ui/JanusMainScreen`** | Native Android Jetpack Compose UI with cybernetic dark mode styling and master server toggle. |

---

## 🧪 TDD++++++ Test Suite

Execute the complete unit and adversarial test suite:

```bash
make test
# Or via Gradle:
./gradlew testDebugUnitTest
```

Tested & Defended Threat Models:
* **Path Traversal & Jail Breakout**: 13+ adversarial traversal payloads (`../../etc/shadow`, URL-encoded `%2e%2e%2f`, null bytes, Windows backslashes).
* **Formula & Code Injection**: Neutralizing CSV/spreadsheet formula payloads (`=`, `+`, `-`, `@`) in uploaded file names by prefixing single-quotes (`'`).
* **Terminal & Log Poisoning**: Stripping raw ANSI CSI escape codes, OSC sequences, and control bytes from synced clipboard text and logcat streams.
* **Brute-Force Mitigation**: Automatic sliding lockout on PIN pairing verification after 5 consecutive failures.
* **Corrupted APK Ingestion**: Rejecting zero-byte files, non-zip binaries, and archives missing `AndroidManifest.xml`.

---

## 🚀 Quick Start & Deployment

### Build Debug APK
```bash
make build
```

### Install to Connected Device
```bash
make install
```

### Run on Device
```bash
make run
```

---

## 📜 Sovereign Provenance & License

* **Author:** RMediaTech / Rick Walker (`<xuoxod@gmail.com>`)
* **Standard:** Sovereign Flagship Security & Architecture Standard (`AGY-RULE-SOVEREIGN-FLAGSHIP-01`)
