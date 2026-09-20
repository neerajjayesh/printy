# Printy · Release 1

**From screen to something real.** Free, open-source Android printing for Epson inkjets connected to a router's USB print server.

Kotlin · Jetpack Compose · Material 3 · Android 8.0+ · GPL-2.0-or-later

This is the historical **v0.1.0** source snapshot. For the current documentation and candidate connection fixes, see [the main branch](https://github.com/neerajjayesh/printy) and [Release 2](https://github.com/neerajjayesh/printy/releases/tag/v0.1.1).

## Download

[Release 1 — v0.1.0](https://github.com/neerajjayesh/printy/releases/tag/v0.1.0) includes the original debug-signed APK and SHA-256 checksum. The application ID is `org.printy.app`, version code 1.

**Known issue:** an Epson L130 connected through an Airtel ZTE ZXHN F670L printed the initial test-page text but did not eject the sheet. Real documents repeatedly stopped around 25%. This build is retained for historical reference; it is experimental and not hardware-certified.

## Features

- Android Print Service integration for the native Print dialog.
- Saved printer profiles, three-step onboarding and test prints.
- PDF/photo import and preview; Gallery share-sheet support.
- Copies, color/grayscale, portrait/landscape and A4/Letter/4 × 6 paper with margins.
- Bounded-memory PDF rasterization at 360 DPI.
- Standalone Kotlin/JVM ESC/P2 encoder, CMYK dithering, PackBits compression and software nozzle interleaving.
- Raw TCP 9100 transport, progress notifications and cancellation.

The listed profiles target Epson L130/L120/L210 using Gutenprint model-80 geometry. L120/L210 have not been physically verified. Borderless printing, LPR and mDNS are not implemented. A reachable port or a “Sent” state cannot confirm physical printing.

## Build

Use JDK 17 or 21, Android SDK platform 35 and build-tools 35.0.0. Configure `ANDROID_HOME` or an untracked `local.properties` with your SDK path.

```sh
./gradlew :escp-encoder:test :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat`. The output is `app/build/outputs/apk/debug/app-debug.apk`. The Gradle wrapper pins version 8.11.1 and its checksum.

The encoder can be tested without an Android SDK:

```sh
./gradlew -PencoderOnly :escp-encoder:test
```

## Architecture

Android spooler → Print Service, or file picker/share sheet → direct print service → shared queue → `PdfRenderer` strips → `escp-encoder` → TCP print server → USB printer.

`app` owns Compose UI, profiles, document import, rasterization and Android service lifecycles. `escp-encoder` has no Android dependencies and writes the printer stream to an `OutputStream`. See [protocol notes](docs/PROTOCOL.md), [contributing](CONTRIBUTING.md) and the [hardware checklist](docs/HARDWARE_VALIDATION.md).

## Privacy and provenance

No ads, accounts, tracking, watermarks or cloud print service. Profiles and document cache stay in app-private storage; job-owned copies are deleted after completion/cancellation/failure. Unused preview/import files expire after 24 hours and are cleaned at startup. Android backup is disabled. Raw printer connections are unencrypted and intended for a trusted local network.

The original APK is preserved as built. Because this version was not committed before 0.1.1 work began, its source snapshot was reconstructed from recorded local edits and rebuilt for validation before tagging. Documentation includes later-discovered issues; the source does not include Release 2's fixes. See [verification](docs/VERIFICATION.md).

## License and credits

GPL-2.0-or-later; see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). ESC/P2 command sequencing and model data are adapted from [Gutenprint](https://gimp-print.sourceforge.io/) and its [reference source](https://github.com/echiu64/gutenprint/tree/a59d99151aeab80d16f64143e0af1f68e48c4af7). Credit goes to Michael Sweet, Robert Krawitz, Charles Briscoe-Smith and the Gutenprint contributors.
