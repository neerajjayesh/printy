# Hardware acceptance checklist

No physical Epson printer was available to the developer during initial implementation. A user has since tested an L130 with the result recorded below. Complete this checklist before claiming verified support or publishing a production release. Do not infer compatibility from passing JVM tests.

Record printer model/firmware, router model/firmware, USB sharing configuration, Android version, app commit, paper and ink. Keep a captured `.prn` stream and a scan/photo of each printed target with personal documents excluded.

## First page and geometry

- [ ] Test page prints with readable text and K/C/M/Y swatches.
- [ ] Primary channels align at the top, center and bottom; specifically check magenta/yellow head offsets.
- [ ] A4 and Letter feed correctly, without early ejection, clipping or an extra blank page.
- [ ] Check exact 1/8-inch insets and a 100 mm scale rule; tune physical offsets only with recorded measurements.
- [ ] Odd raster widths leave padding unprinted.
- [ ] Two-page and two-copy jobs preserve page order and collate copies.
- [ ] Landscape rotates content correctly on the same short-edge-fed physical sheet.
- [ ] 4 × 6 paper prints with the advertised margin. Borderless remains unavailable.
- [ ] Grayscale has no colored ink fringes and neutral ramps do not band excessively.
- [ ] Dense photographic regions have acceptable drying and ink coverage on supported media.

## Android integration

- [ ] Enable service and discover saved profiles from Chrome, Docs, Photos/Gallery and Files.
- [ ] Test whole document, page 2 alone, a noncontiguous range, portrait/landscape, color/gray and copies in the native dialog.
- [ ] Print a large PDF with bounded memory; rotate the activity during preview and during printing.
- [ ] Preview and output agree in page fit and margins; full-page native PDF layouts do not receive duplicate margins.
- [ ] Import photos with EXIF rotations 1–8 and check against Gallery.
- [ ] Cancellation works before connect, while queued, during rasterization and during a blocked socket write.
- [ ] Notifications work with permission granted and denied; native Android job state matches outcomes.
- [ ] Closing the UI does not stop an active job; notification cancellation does.
- [ ] Killing the process mid-job never silently replays already sent pages.
- [ ] Delete or edit a profile during discovery; Android removes/updates the corresponding destination.

## Failures

- [ ] Disconnect Wi-Fi, switch networks, use wrong IP/port and power off the printer; messages remain readable.
- [ ] Unplug printer USB while router remains reachable; verify the app does not claim physical completion.
- [ ] Test a router which accepts the stream but has no attached printer; “sent” is not interpreted as paper output.
- [ ] Stall a TCP receiver; the 120-second write deadline closes the connection.
- [ ] Confirm the last-page form feed and footer reach the router, and that finishing the connection ejects the sheet. Test both servers that close their side and servers that keep it open.
- [ ] Cancel while “Finishing the connection”; the app stops promptly without claiming paper output.
- [ ] Password-protected, corrupt, empty and >150 MB PDFs fail clearly.
- [ ] Real paper/ink/jam conditions are explained via the printer's indicators; no unsupported status detection is claimed.

## Results

| Printer | Router | Android | Commit | Result / evidence |
|---|---|---|---|---|
| L130 | Airtel ZTE ZXHN F670L GPON ONT; firmware not supplied | Not yet supplied | 0.1.0 local debug APK | User reports all test-page text printed, including final two lines, but paper remained partway inside and power light blinked for at least five minutes. Real documents repeatedly stopped around 25%; new submissions started another partial print. Complete job failed; no verified fix yet. |
| L120 | Pending | Pending | Pending | Not hardware-validated |
| L210 | Pending | Pending | Pending | Not hardware-validated |

Version 0.1.1 adds a bottom-of-page marker, corrects `LD`/`JE` ordering, drains replies and waits after output shutdown. These are candidate fixes for the L130 report, not confirmation of its root cause. Install the update over 0.1.0, clear the stalled job with the printer's Stop button, and send one fresh test page. Record whether the bottom marker prints, the sheet ejects and the power light returns to normal, alongside the copied job details. Do not send repeated jobs into an uncleared stalled page.
