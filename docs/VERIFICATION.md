# Release 1 verification

The original v0.1.0 APK built successfully with JDK 21, Gradle 8.11.1, Android SDK platform 35 and build-tools 35.0.0. The application targets Android API 35 with minimum API 26.

- 12 encoder JVM tests and 4 app JVM tests passed.
- Android lint: 0 errors; 8 dependency-update suggestions.
- APK Signature Scheme v2 verified; GPL and third-party notices are included in APK assets.
- Instrumentation tests compiled but were not run locally on a device/emulator.

Before creating this tag, the v0.1.0 source was reconstructed from recorded local edits and built independently. All 16 JVM tests passed again and the debug APK assembled successfully. The original APK is retained as the release asset rather than replaced with this reconstruction build. Rebuilding is not claimed to produce a byte-identical APK.

The L130/Airtel ZTE ZXHN F670L hardware test did not complete successfully: the test-page text printed but the sheet stayed inside, and longer documents repeatedly stopped near 25%. L120 and L210 were not physically tested. See the [hardware report](HARDWARE_VALIDATION.md). Release 2 contains candidate fixes; this historical source retains the original implementation.
