# Player Maintainability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce PlayerScreen responsibility without changing any playback gesture or lifecycle behavior.

**Architecture:** Extract pure formatting/end-action decisions and stateless control composables. Keep AndroidView, HorizontalPager, pointer input, effects, and engine call ordering in `PlayerScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, LibVLC wrapper, JUnit 4

**Spec:** `docs/superpowers/specs/2026-08-23-quality-foundation-design.md`

## Global Constraints

- `PlayerScreen` public parameters and parent-owned navigation remain unchanged.
- Existing gesture thresholds, control positions, and engine lifecycle order remain unchanged.
- No second player, surface, or speculative open is introduced.

---

### Task 1: Pure playback presentation decisions

**Files:**
- Create: `app/src/main/java/com/pigfarmerjc/galleryplayer/PlayerPresentation.kt`
- Create: `app/src/test/java/com/pigfarmerjc/galleryplayer/PlayerPresentationTest.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/PlayerScreen.kt`

**Interfaces:**
- Produces: `formatPlayerTime(ms)`, `playbackEndAction(mode, index, size)`, and sealed `PlaybackEndAction`.

- [ ] **Step 1: Write failing tests for time formatting and NONE/ONE/ALL end actions including empty and last-item lists.**
- [ ] **Step 2: Run focused tests and confirm unresolved functions.**
- [ ] **Step 3: Implement pure decisions and replace matching inline branches in PlayerScreen.**
- [ ] **Step 4: Run player gesture, seek, repeat, and presentation tests.**
- [ ] **Step 5: Commit `refactor: extract player presentation decisions`.**

### Task 2: Stateless control overlay extraction

**Files:**
- Create: `app/src/main/java/com/pigfarmerjc/galleryplayer/PlayerControls.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/PlayerScreen.kt`
- Test: `app/src/androidTest/java/com/pigfarmerjc/galleryplayer/MainActivityTest.kt`

**Interfaces:**
- Produces: focused top controls, timeline controls, and transient feedback composables with event callbacks only.
- Consumes: immutable state collected by PlayerScreen and existing callbacks.

- [ ] **Step 1: Add UI assertions for the existing control labels and actions before extraction.**
- [ ] **Step 2: Run the focused connected test on the tablet and verify it exercises the current overlay.**
- [ ] **Step 3: Move stateless composable blocks without changing modifiers, semantics, or callback bodies.**
- [ ] **Step 4: Re-run the focused connected test and all JVM tests.**
- [ ] **Step 5: Commit `refactor: split player controls from playback host`.**

### Task 3: Player regression verification

- [ ] **Step 1: Run assembleDebug, lintDebug, and app connected tests.**
- [ ] **Step 2: On the tablet verify open A, page B, dismiss, open C, double-tap seek, hold speed, pause, resume, and back.**
- [ ] **Step 3: Confirm logcat has no current-process crash, ANR, or duplicate attach exception.**

