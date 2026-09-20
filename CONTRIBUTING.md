# Contributing

Contributions are welcome under GPL-2.0-or-later. Preserve upstream notices and add provenance when porting printer-specific logic.

Run the encoder tests, Android JVM tests and lint before submitting a change. Changes to rendering need device/emulator tests; printer protocol changes additionally need a recorded hardware run. Reference-derived byte tests are useful but do not replace printer testing.

Keep user-facing language simple. Do not add tracking, ads, monetization, proprietary dependencies, or network traffic beyond the user's selected printer and explicitly chosen document provider. Keep printer-model capabilities conservative and profile-specific.

For bug reports, include app/Android versions, exact printer/router model, expected versus actual output, a minimal non-sensitive sample and reproduction steps. Never attach personal documents or LAN credentials. Avoid reporting physical printing as successful solely because TCP accepted the bytes.
