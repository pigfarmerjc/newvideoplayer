# Runtime Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove avoidable thumbnail and synchronization work while preserving gallery and storage behavior.

**Architecture:** Add pure, independently tested coordination and sync-planning units, then connect them to the existing Android cache and storage manager. Keep LibVLC and UI behavior unchanged.

**Tech Stack:** Kotlin 2.3.20, coroutines 1.10.2, Android LruCache, Room 2.8.4, JUnit 4

**Spec:** `docs/superpowers/specs/2026-08-23-quality-foundation-design.md`

## Global Constraints

- Preserve all existing interactions and playback features.
- Decode concurrency remains three.
- Failed or cancelled thumbnail requests must be retryable.
- Preserve database IDs, favorites, hidden flags, history relations, and volume-scoped deletion.

---

### Task 1: Constant-time thumbnail fallback index

**Files:**
- Create: `app/src/main/java/com/pigfarmerjc/galleryplayer/ThumbnailKeyIndex.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/ThumbnailLoader.kt`
- Test: `app/src/test/java/com/pigfarmerjc/galleryplayer/ThumbnailKeyIndexTest.kt`

**Interfaces:**
- Produces: `ThumbnailKeyIndex.record(uri, key)`, `remove(key)`, and `latestKey(uri): String?`.
- Consumes: cache insertion and eviction callbacks from `ThumbnailCache`.

- [ ] **Step 1: Write failing tests for latest-key lookup, replacement, stale eviction, and current eviction.**
- [ ] **Step 2: Run `gradlew :app:testDebugUnitTest --tests *ThumbnailKeyIndexTest` and confirm unresolved production type failure.**
- [ ] **Step 3: Implement the synchronized two-map index without parsing URI text from cache keys.**
- [ ] **Step 4: Connect `LruCache.put` and `entryRemoved` to the index; replace `snapshot().entries.firstOrNull` with exact key lookup.**
- [ ] **Step 5: Run focused and full app unit tests, then commit `perf: make thumbnail fallback lookup constant time`.**

### Task 2: Same-key thumbnail request coalescing

**Files:**
- Create: `app/src/main/java/com/pigfarmerjc/galleryplayer/ThumbnailRequestCoordinator.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/ThumbnailLoader.kt`
- Test: `app/src/test/java/com/pigfarmerjc/galleryplayer/ThumbnailRequestCoordinatorTest.kt`

**Interfaces:**
- Produces: `suspend fun <T> load(key: String, cached: () -> T?, loader: suspend () -> T?): T?`.
- Consumes: exact cache reads and existing bounded Android decode block.

- [ ] **Step 1: Write a coroutine test launching 20 same-key requests and asserting one loader invocation and identical results.**
- [ ] **Step 2: Add failure/cancellation tests proving a later request retries.**
- [ ] **Step 3: Run focused tests and confirm the coordinator is missing.**
- [ ] **Step 4: Implement a keyed mutex with reference-counted entries and a second cache check inside the key lock.**
- [ ] **Step 5: Route `loadMediaThumbnail` through the coordinator while retaining the three-slot semaphore.**
- [ ] **Step 6: Run tests and commit `perf: coalesce duplicate thumbnail decodes`.**

### Task 3: Pure synchronization diff planner and batch persistence

**Files:**
- Create: `core-storage/src/main/java/com/pigfarmerjc/galleryplayer/core/storage/sync/SyncDiffPlanner.kt`
- Create: `core-storage/src/test/java/com/pigfarmerjc/galleryplayer/core/storage/SyncDiffPlannerTest.kt`
- Modify: `core-storage/src/main/java/com/pigfarmerjc/galleryplayer/core/storage/sync/StorageSyncManager.kt`
- Modify: `core-database/src/main/java/com/pigfarmerjc/galleryplayer/core/database/dao/Daos.kt`
- Modify: `core-database/src/main/java/com/pigfarmerjc/galleryplayer/core/database/repository/Repositories.kt`
- Modify: `core-storage/src/test/java/com/pigfarmerjc/galleryplayer/core/storage/FakeMediaRepository.kt`

**Interfaces:**
- Produces: `SyncDiffPlanner.plan(existing, scanned): SyncDiff` with `upserts`, `deletedUris`, `insertedCount`, and `updatedCount`.
- Produces: `MediaRepository.saveScannedMediaItems(items)` for already-merged scanner data.

- [ ] **Step 1: Write failing tests for insert/update/delete/no-change and favorite/hidden/database-ID preservation.**
- [ ] **Step 2: Run focused tests and confirm the missing planner failure.**
- [ ] **Step 3: Implement the O(n) planner using URI maps and sets.**
- [ ] **Step 4: Add a dedicated Room batch `@Upsert` path; leave normal `saveMediaItems` semantics intact for playback calls.**
- [ ] **Step 5: Replace the StorageSyncManager comparison loop with the planner and batch path.**
- [ ] **Step 6: Run core storage tests, Room connected tests, and commit `perf: batch incremental media synchronization`.**

### Task 4: Runtime verification

**Files:**
- Modify: `docs/performance/tablet-baseline.md`

- [ ] **Step 1: Run all JVM tests, assembleDebug, and lintDebug.**
- [ ] **Step 2: Run app and database connected tests on the tablet.**
- [ ] **Step 3: Install the APK, repeat the 12-swipe workload, and record FrameTimeline and memory beside the original 3.02%/377 MB PSS baseline.**
- [ ] **Step 4: Commit `docs: record tablet runtime verification`.**

