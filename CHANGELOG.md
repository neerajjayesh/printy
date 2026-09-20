# Changelog

## Release 3 — 0.2.0

- Choose all pages, custom lists/ranges, odd pages or even pages. Invalid selections explain how to correct them before printing.
- Fit two pages side by side or four pages in a grid, with optional reverse page order.
- Preview the assembled sheets, including unused blank spaces. Two-page mode defaults to landscape.
- Count physical sheets in the print button and progress; collate copies of the selected layout.
- Keep native Android spooler subsets and existing single-page geometry intact.
- Preserve the Release 2 socket transport and ESC/P2 encoder. The owner now reports that Release 2 works well on the L130/F670L setup.
- Add JVM page-plan tests and Android rendering tests for clipping, page selection, rotation, strip boundaries, grayscale and trailing blanks.

The new layouts require a physical print check. This remains a debug-signed development build, installable over Releases 1 and 2.

## Release 2 — 0.1.1

An experimental update responding to the L130/ZTE F670L partial-print and missing-ejection reports.

- Correct the ESC/P2 footer order to `LD`, then `JE`.
- Drain server replies while writing; half-close output and wait up to 90 seconds before fully closing.
- Allow up to 120 seconds for blocked writes; keep cancel responsive during the finishing wait.
- Stop automatic probes to an endpoint after a print attempt for the rest of the app process.
- Add copyable job details and a test-page bottom marker.
- Expand JVM coverage to 22 passing tests: 13 encoder and 9 app tests.

At publication, hardware confirmation was pending. The owner has since reported that the app works well on the L130/F670L setup. This is a debug-signed development build.

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

The first two release APKs are the original local builds, preserved without rebuilding or re-signing for publication. Release 3 is a new local build from its tagged source. The adjacent SHA-256 files identify the binaries. Version 0.1.0 had not been committed before work on 0.1.1, so its source snapshot was reconstructed from the recorded local edits and rebuilt for validation before tagging. The version tags have separate source snapshots; no dates or earlier Git history are fabricated. Documentation at publication includes subsequently reported issues.
