# Tablet performance baseline

Measured on 2026-08-23 using an ALLDOCUBE iPlay 70 mini Ultra (Android 14 / API 34) with a 6,424-video library and the debug build.

## Current result

| Scenario | Result |
| --- | ---: |
| Cold activity launch (`am start -W`) | 683 ms |
| 12 repeated 350 ms library swipes | 491 frames |
| Modern jank | 16 frames / 3.26% |
| Frame-time p50 / p90 / p95 / p99 | 9 / 16 / 20 / 25 ms |
| Process memory after scrolling | 406,749 KB PSS / 511,560 KB RSS |

The initial reference run before the thumbnail and synchronization changes launched in 660–738 ms and produced p50/p90/p95/p99 frame times of 9/18/21/28 ms. Modern jank was 3.02% in that run. Results are close enough to confirm no scrolling regression; the tail frame times improved slightly.

## Reproduction

1. Force-stop the application and launch `com.pigfarmerjc.galleryplayer/.MainActivity` with `adb shell am start -W`.
2. Wait for the initial library to settle, then reset `dumpsys gfxinfo`.
3. Perform 12 vertical 350 ms swipes and wait two seconds.
4. Capture `dumpsys gfxinfo` percentiles/jank and `dumpsys meminfo` totals.

Debug-build memory includes Compose inspection/runtime overhead and the in-memory thumbnail cache, so release-build memory should be tracked separately before publishing.
