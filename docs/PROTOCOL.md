# ESC/P2 implementation notes

## Scope and provenance

The initial backend targets the command-2000, variable-drop, 360 DPI configuration shared by Gutenprint's L120/L130/L210 entries (model 80, `c82` ink group). Reference snapshot: `a59d99151aeab80d16f64143e0af1f68e48c4af7` from the Gutenprint mirror linked in `THIRD_PARTY_NOTICES.md`. The GPL license covers this Kotlin adaptation.

This is a restricted implementation with an independently written fixed software weave. It does not reproduce Gutenprint's adaptive weave, density curves, high-resolution multi-pass schemes, bidirectional calibration or media-specific color processing. Real-device conformance remains unverified. Wire fixtures validate the documented format and source-derived constants, not physical output.

## Page model

- Physical DPI: 360 × 360.
- Page sizes: A4, Letter, 4 × 6 inches. Milliinches round to the nearest pixel.
- Imageable inset: 45 pixels (1/8 inch) on all sides.
- Logical landscape content rotates into a portrait physical sheet. The transport never tells an 8.5-inch printer to feed a sheet 11 inches wide.
- Graphics mode `ESC ( G`, extended units `ESC ( U`: base 1440; page/vertical/horizontal factors all 4.
- Color geometry `ESC ( K 00 02` remains enabled for grayscale, with only the black channel populated. This avoids changing nozzle geometry halfway through the backend.
- Software weave `ESC ( i 00`, unidirectional `ESC U 01`, drop table `ESC ( e 00 10`.
- Head resolution `ESC ( D`: base 14400, vertical spacing 80, horizontal spacing 40. The vertical spacing is two 360-DPI rows, not one raster row.
- `ESC ( C` and `ESC ( S` include the model's 24-point extra bottom. The signed top margin includes model-80's -240-row initial vertical offset. These device-specific values must be checked against actual paper alignment.

## Raster packing

Channels are K=0, C=2, M=1, Y=4. Two-bit samples are packed most significant pixel first, four pixels per byte. Raster width is `2 * ceil(pixelWidth / 8)`, preserving the eight-pixel padding used by the Gutenprint variable-drop path. All padding bits are zero. Codes 00, 01 and 11 represent no drop, small drop (relative level .28) and large drop (1.0); code 10 is not used by this resolution table.

`ESC i COLOR 01 02 BYTESlo BYTEShi LINESlo LINEShi` introduces compressed two-bit raster data. TIFF PackBits is applied independently to each nozzle row: 0..127 introduce 1..128 literal bytes, 129..255 repeat one byte 128..2 times. The encoder never emits reserved control 128. Raster data is followed by **CR 0x0d**; some old ESC/P2 manual text accidentally labels CR as 0x0a.

## Software weave

For 59 active nozzles at pitch 2, each 118-row block uses phases 0 and 1. The 60th transmitted nozzle row is blank padding to satisfy `MinNozzles=60`. Channel head offsets are K=0, C=0, M=120 and Y=240 rows. For a pass at feed F, nozzle N draws source row:

```text
sourceRow = F + N * 2 - channelHeadOffset
```

Negative and out-of-page rows are blank. Feed advances monotonically. Blank channel passes are omitted; the next emitted pass advances by the accumulated distance. A rolling cache retains only rows needed for the offset compensation and current pass. The independent test parser reconstructs the source rows from actual encoded command bytes and checks for missing or duplicate rows across every channel, including the trailing partial block.

## Job framing

EJL exits packet mode, then reset and REMOTE1 PM/SN initialize the job and plain-paper feed. Each page sets its graphics/page state and ends with form feed. The job ends with reset, REMOTE1 **LD then JE** and remote-mode exit. This matches `stpi_escp2_deinit_printer`: load NVRAM settings, then the model-80 `postinitRemoteSequence`. Version 0.1.0 reversed those two remote commands; 0.1.1 corrects the order. All multi-byte arguments are little endian; signed offsets use two's complement.

On cancellation or I/O failure, do not emit a normal form feed/footer: close the connection. Sending extra bytes after a partial compressed block could be interpreted as raster data. Already accepted bytes cannot be recalled, and a user may need the printer's cancel button after an interrupted stream. Never automatically replay the whole job.

## TCP completion

The Android transport reads and discards replies concurrently with writes, retaining only byte counts. After flushing the final form feed and complete footer, it calls `Socket.shutdownOutput()` and allows up to 90 seconds for peer EOF before full close. This follows the general AppSocket completion approach illustrated by [CUPS](https://github.com/OpenPrinting/cups/blob/master/backend/socket.c), implemented independently in Kotlin with a bounded total wait. An unread receive queue can trigger a reset on socket close in [Linux TCP](https://github.com/torvalds/linux/blob/master/net/ipv4/tcp.c); a write-only client can also block a bidirectional bridge that needs to send status replies. Neither peer EOF nor timeout confirms page ejection. Connection resets are surfaced as errors, and cancellation interrupts the wait immediately.

Loopback regressions exercise large concurrent replies, complete footer delivery before EOF, delayed server closure, a server that stays open, cancellation during the finishing wait and peer reset. These simulate transport behavior, not a physical USB printer. Automatic TCP probes stop for an endpoint after a print attempt for the rest of the process; a completed socket transfer does not mean the physical printer is idle.

## Color limitations

ARGB composites onto white. RGB is converted to CMYK with full common-component black extraction (neutral gray uses black, avoiding composite-color gray). Gray mode uses a luminance-weighted black channel. Serpentine Floyd–Steinberg diffusion carries row errors across renderer strip boundaries. This deterministic baseline preserves average coverage, but calibrated photo color needs paper/ink-specific curves and hardware comparison.

## Sources

- [Gutenprint ESC/P2 driver](https://github.com/echiu64/gutenprint/blob/a59d99151aeab80d16f64143e0af1f68e48c4af7/src/main/escp2-driver.c)
- [Model 80](https://github.com/echiu64/gutenprint/blob/a59d99151aeab80d16f64143e0af1f68e48c4af7/src/xml/escp2/model/model_80.xml)
- [C82 ink configuration](https://github.com/echiu64/gutenprint/blob/a59d99151aeab80d16f64143e0af1f68e48c4af7/src/xml/escp2/inks/c82.xml)
- [Two-bit folding](https://github.com/echiu64/gutenprint/blob/a59d99151aeab80d16f64143e0af1f68e48c4af7/src/main/bit-ops.c)
- [Developer manual](https://gimp-print.sourceforge.io/gutenprint-developer-manual.pdf)
