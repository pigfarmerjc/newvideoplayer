# GalleryPlayer Quality Foundation Design

## Objective

Improve maintainability, large-library performance, and visual polish without changing the app's existing interaction model or playback feature set.

## Non-negotiable behavior

- Keep tap-to-open, horizontal video paging, bidirectional vertical dismiss, single-tap controls, double-tap seek, hold-for-speed, resume, repeat, favorites, picture-in-picture, track selection, scale modes, TF card/SAF access, and decoder selection.
- Keep the parent-owned `currentIndex`, `videoUri`, and `videoList` flow.
- Keep one LibVLC playback engine and one attached output host for the active video.
- Do not add shared-element transitions, a second decoder, network playback, or speculative media preloading.
- Every behavioral extraction starts with a failing unit test. Existing interaction tests remain unchanged and must pass.

## Subproject A: Runtime foundation

### Thumbnail pipeline

The existing bitmap LRU cache is retained, but URI fallback lookup becomes O(1) instead of copying and scanning the full cache snapshot. A small generic index owns the relationship between content URI and the most recently cached size key. Cache eviction removes only the matching current index entry.

Concurrent requests for the same URI/size share one in-flight load. The loader reuses the exact cached bitmap when available and never starts multiple MediaStore or `MediaMetadataRetriever` decodes for the same key. Decode concurrency remains bounded at three.

The gallery continues requesting size-aware square thumbnails. No visual behavior or cropping rule changes.

### Database synchronization

MediaStore synchronization keeps the same inserted, updated, deleted, favorite, hidden, history, and folder semantics. Existing database items are joined to scanner results in memory once. Batch `@Upsert` replaces per-item select/update calls for synchronization, while user flags are copied from existing records before the batch write.

Folder cover calculation reads the final volume once and groups it in memory, replacing one query per folder. Repository APIs used outside synchronization keep their current safe behavior.

### Performance evidence

Add deterministic unit tests for cache indexing/request coalescing and synchronization merge rules. Keep an adb-based tablet verification checklist recording cold start, 6424-item grid scroll FrameTimeline results, memory, and crash/ANR output. Release performance is judged against the 90 Hz device's 11 ms frame budget; results are reported rather than hidden behind a pass/fail threshold.

## Subproject B: Player maintainability

`PlayerScreen` remains the public composable and retains its parameters. Pure decisions move into focused files:

- playback completion/repeat decisions;
- pager-to-parent index decisions;
- dismiss threshold/target decisions;
- control presentation components and formatting.

The AndroidView host lifecycle, gesture recognizer, pager, and LibVLC calls are not redesigned in this pass. Extraction order follows the current data flow so recomposition keys and call ordering remain identical. Existing gesture and integration tests are supplemented with pure decision tests.

## Subproject C: Engineering and visual polish

Remove Windows repair scripts and registry backups from the Android repository after copying the only backup set to a user-level folder outside the repo. Update stale project documentation to match the current product.

Migrate the AGP 9 project away from deprecated external Kotlin compatibility switches only if a clean build proves the built-in Kotlin configuration works across every module. This migration stays in its own commit and is reverted if any module or Room/KSP task regresses.

Visual changes are deliberately small: reduce excessive toolbar/bottom-bar vertical space, strengthen title/action hierarchy, use consistent scrims and touch targets, and keep all control locations and gestures recognizable. No navigation, command, or gesture is removed or reassigned.

## Error handling

- Thumbnail decode failure remains a placeholder, with failed work removed from the in-flight map so later requests can retry.
- Cancellation propagates and never leaves a permanently blocked request key.
- Sync cancellation stops before destructive deletion or folder recomputation where possible.
- LibVLC host operations retain the current idempotent guard and main-thread serialization.

## Verification

For each subproject:

1. Run the new focused unit tests and observe the pre-implementation failure.
2. Run all JVM tests, `:app:assembleDebug`, and Android Lint.
3. Run `:app:connectedDebugAndroidTest` on the ALLDOCUBE iPlay 70 mini Ultra.
4. Install the final debug APK and verify open A, page to B, dismiss, open C, resume, search/sort ordering, and grid position retention.
5. Capture post-change scroll FrameTimeline and memory numbers using the same 12-swipe workload as the baseline.

## Completion criteria

- Existing interaction behavior is preserved on the tablet.
- No crash or ANR occurs in the regression flow.
- All automated tests and the debug build pass.
- Thumbnail same-key work is coalesced and URI fallback lookup is constant time.
- Synchronization avoids per-item and per-folder query loops in the hot path.
- `PlayerScreen` and `MainActivity` responsibilities are materially smaller or more focused without public behavior changes.
- Repository contains no machine-specific Windows repair artifacts.
- Final APK path, commits, before/after performance evidence, and remaining risks are reported.
