# Verification

## Release 3 — 0.2.0

This build adds page selection and sheet layouts. The ESC/P2 encoder and socket transport are unchanged from Release 2.

| Check | Result |
| --- | --- |
| Debug app and instrumentation APKs | Built successfully |
| Encoder JVM tests | 13 passed |
| App JVM tests | 20 passed: 11 page-plan tests and 9 transport/profile tests |
| Android debug lint | 0 errors; 8 dependency-update suggestions (`GradleDependency`) |
| APK signature | Verified using APK Signature Scheme v2 |
| Upgrade compatibility | Same application ID and certificate as Releases 1 and 2; version code 3 |
| Android emulator tests | 6 passed on Android API 35; [CI run](https://github.com/neerajjayesh/printy/actions/runs/35497491214) |
| Physical printing of new layouts | Requires user testing |

Page-plan tests cover one-based ranges, en dashes, overlaps, invalid/out-of-bounds inputs, odd/even filtering, reverse order before grouping, final blank cells, copies and preserving the spooler's own page subset. Android tests exercise actual `PdfRenderer` output for two-page rotation, four-page clipping, strip boundaries, selected/reversed pages, trailing blanks and grayscale preview, plus the existing single-page strip regression.

All three CI jobs passed for feature commit `cd58cd8503f485e8c33d2f191e894832f7f9d3d8`. The release adds verification documentation only after that run; application, test and build code are unchanged. Both legal notice assets were also verified in the packaged APK.

Local build: Windows, JDK 21, Java/Kotlin target 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, Android SDK 35 / build-tools 35.0.0. Minimum SDK 26, target SDK 35.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :escp-encoder:test :app:testDebugUnitTest :app:lintDebug --console=plain --no-daemon
```

The local run used the checksum-verified Gradle installation under `.tools/gradle-ready/gradle-8.11.1`; `BUILD SUCCESSFUL`, 85 actionable tasks. Log: `.tools/layout-build.log`. Reports are under `app/build/reports` and `escp-encoder/build/reports`.

Release binary: `dist/printy-0.2.0-debug.apk`, 10,559,898 bytes.

SHA-256:

```text
35dad0b9c39b07a73e4d813113cc92ee347a8850344c25fcbf25a9ae05139963
```

Signing certificate SHA-256 (same across all three published APKs):

```text
0adc782c4c2106a7228413ee7e1ca4a0989101ef7e9ec116bfff07d2a8ae1a56
```

The release download is a debug-signed development build. CI artifacts use their own ephemeral debug signing keys and should not be substituted for an update signed with this certificate. Generated binaries, SDK paths and signing keys are ignored by Git.

### Updated hardware evidence

After the Release 2 connection/footer changes, the owner reports that the app works well on the Epson L130 / Airtel ZTE ZXHN F670L setup. This is a successful user report, not completion of every media, geometry or failure-mode check. Release 3's selection and multi-page layouts still require a physical test. L120/L210 profiles remain unverified on hardware.

The [subsequent Release 2 CI run](https://github.com/neerajjayesh/printy/actions/runs/35495577270) also passed, including the original single-page strip test on Android API 35. This updates the original local-only record below.

## Original Release 2 local verification record

The following records what was known at the time of that local build; see the updated evidence above for later user reports and emulator results.

These checks cover the original local Printy 0.1.1 build produced after investigating the reported L130/Airtel ZTE ZXHN F670L stalls. The APK is preserved as the [Release 2 download](https://github.com/neerajjayesh/printy/releases/tag/v0.1.1). Consult the repository's Actions runs separately for subsequent CI results.

## Completed checks

| Check | Result |
| --- | --- |
| Debug application APK | Built successfully |
| Android instrumentation test APK | Built successfully; not executed on a device |
| Standalone encoder JVM tests | 13 passed, no failures or errors |
| App JVM tests | 9 passed, no failures or errors |
| Android debug lint | 0 errors; 8 dependency-update suggestions (`GradleDependency`) |
| APK signature | Verified using APK Signature Scheme v2 |
| Upgrade compatibility | Same application ID and signing certificate as 0.1.0; version code increased from 1 to 2 |
| Bundled legal notices | `assets/LICENSE` and `assets/THIRD_PARTY_NOTICES.md` verified in APK |

The encoder tests check reference-derived protocol fixtures, compression, dithering, raster reconstruction, cancellation and form feed before the corrected LD/JE footer. App tests cover address validation, loopback socket delivery, cancellation of a blocked write, write deadlines, concurrent large server replies, footer delivery before EOF, waiting for delayed server closure, bounded completion when a server remains open, cancellation during finishing and connection-reset detection. These checks do not establish physical printer compatibility.

## Build environment and command

- Windows with JDK 21; Java/Kotlin bytecode target 17.
- Gradle 8.11.1, Android Gradle Plugin 8.9.2 and Kotlin 2.1.20.
- Android SDK platform 35 and build-tools 35.0.0.
- Application ID `org.printy.app`, version 0.1.1 (2), minimum SDK 26, target SDK 35.

Equivalent reproducible command from the project root:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :escp-encoder:test :app:testDebugUnitTest :app:lintDebug --console=plain --no-daemon
```

The local run used the checksum-verified Gradle 8.11.1 installation under `.tools/gradle-ready/gradle-8.11.1` and ended with `BUILD SUCCESSFUL` (85 actionable tasks). The Gradle wrapper pins the same distribution version and checksum.

## Local outputs

- `dist/printy-0.1.1-debug.apk`: debug-signed installable app update.
- `dist/printy-0.1.1-debug.apk.sha256`: SHA-256 of that APK.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: instrumentation test APK.
- `app/build/reports/lint-results-debug.html`: lint report.
- `escp-encoder/build/reports/tests/test/index.html`: encoder test report.
- `app/build/reports/tests/testDebugUnitTest/index.html`: app unit test report.
- `.tools/connection-fix-build.log`: local 0.1.1 build log.

The APK is a development build. Release signing remains the repository owner's choice when publishing. Generated outputs and machine-specific SDK paths are ignored by Git.

## Still requires a device and printer

No Android device or emulator was connected for this verification. The instrumentation tests compile, but their PDF strip-rendering assertions have not been executed. On-device UI, native print-dialog integration, foreground notifications and Android lifecycle behavior also need runtime verification.

No physical Epson printer was available to the developer. The user reports that 0.1.0 printed the complete test-page text on an L130 but failed to eject the sheet, and that real documents repeatedly stopped around 25% using an Airtel ZTE ZXHN F670L. Version 0.1.1 corrects footer ordering, drains replies, finishes the connection with a bounded wait, extends the blocked-write deadline and stops automatic probes after a print attempt. It adds a bottom-page marker and locally copied job details. These changes are tested in software; their effect on that physical setup has not yet been confirmed.

L130, L120 and L210 profiles remain experimental. The byte fixtures come from documented upstream command logic, not a captured and physically verified L130 job. Use the [hardware validation checklist](HARDWARE_VALIDATION.md) to verify feed geometry, head alignment, color output, cancellation and router compatibility before treating a model as supported on hardware.

Successful TCP delivery cannot establish that a page physically printed or detect paper/ink errors without a supported status backchannel. Borderless printing, mDNS discovery and LPR remain future work.
