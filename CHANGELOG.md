# Changelog

## Release 2 — 0.1.1

An experimental update responding to the L130/ZTE F670L partial-print and missing-ejection reports.

- Correct the ESC/P2 footer order to `LD`, then `JE`.
- Drain server replies while writing; half-close output and wait up to 90 seconds before fully closing.
- Allow up to 120 seconds for blocked writes; keep cancel responsive during the finishing wait.
- Stop automatic probes to an endpoint after a print attempt for the rest of the app process.
- Add copyable job details and a test-page bottom marker.
- Expand JVM coverage to 22 passing tests: 13 encoder and 9 app tests.

Hardware confirmation of the fix is pending. This is a debug-signed development build.

## Release 1 — 0.1.0

Initial experimental implementation.

- Kotlin/Compose/Material 3 Android app, Android 8.0+.
- Native Android Print Service, profile management and onboarding.
- PDF/photo import, preview, basic options and share-sheet support.
- Standalone 360 DPI ESC/P2 encoder for L130/L120/L210 profiles.
- TCP 9100 transport, progress notifications and cancellation.
- 16 passing JVM tests: 12 encoder and 4 app tests.

Known issue: an L130 connected to an Airtel ZTE ZXHN F670L printed test-page content but failed to eject the sheet. Longer documents stopped around 25%. This version is retained as a historical download.

## Source and binary provenance

Both release APKs are the original local builds, preserved without rebuilding or re-signing for publication. Their adjacent SHA-256 files identify those binaries. Version 0.1.0 had not been committed before work on 0.1.1, so its source snapshot was reconstructed from the recorded local edits and rebuilt for validation before tagging. The version tags have separate source snapshots; no dates or earlier Git history are fabricated. Documentation at publication includes subsequently reported issues.
