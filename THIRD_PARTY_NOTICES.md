# Third-party notices

Printy is licensed under **GPL-2.0-or-later**, including its Kotlin encoder and Android application. See `LICENSE` for the full license. A port to a different language does not remove the upstream license obligations.

## Gutenprint

Project: https://gimp-print.sourceforge.io/

Source reference: https://github.com/echiu64/gutenprint/tree/a59d99151aeab80d16f64143e0af1f68e48c4af7

The command sequencing and model constants were adapted into Kotlin from this pinned Gutenprint source snapshot. The implementation is deliberately smaller than the original driver; it is not a complete Gutenprint port. The serpentine error diffusion implementation follows the established algorithm and Gutenprint's per-channel approach, without its adaptive/hybrid tuning or color management.

Relevant upstream files:

- `src/main/escp2-driver.c`: command construction and sequencing. Copyright 1997–2000 Michael Sweet and Robert Krawitz.
- `src/main/print-escp2.c`: resolution, dimensions, head offset setup. Copyright 1997–2000 Michael Sweet and Robert Krawitz.
- `src/main/print-weave.c`: software weave concepts and channel-offset convention. Copyright 2000 Charles Briscoe-Smith.
- `src/main/dither-ed.c`: per-channel error diffusion concepts. Copyright 1997–2003 Michael Sweet and Robert Krawitz.
- `src/xml/escp2/model/model_80.xml`, `model/base/baseline_360.xml`, `inks/c82.xml`: model geometry, ink channel IDs and drop levels. Copyright 2008 Robert Krawitz.
- `src/xml/printers/escp2.xml`: L120/L130/L210 model mapping.
- `doc/developer/escp2.xml`: wire-format reference.

These upstream implementation files are distributed under GNU GPL version 2 or, at your option, any later version, without warranty. Their copyright notices are preserved here, and `LICENSE` includes the license text. Printy modifications and Kotlin implementation: Copyright 2026 Printy contributors.

## Runtime and build dependencies

Kotlin and kotlinx.coroutines (JetBrains), AndroidX/Jetpack Compose/Material Components (Android Open Source Project), and Gradle are Apache-2.0 licensed. JUnit 4 is EPL-1.0 licensed and used only in tests. The Gradle wrapper scripts retain their upstream copyright/license header; the wrapper JAR is from Gradle 8.11.1. Android platform APIs are provided by the device. No Gutenprint native binary, analytics SDK, advertising SDK, proprietary Epson SDK or external PDF renderer is bundled.

Epson is a trademark of Seiko Epson Corporation. Printy is an independent community project.
