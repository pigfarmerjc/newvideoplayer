# Engineering and Visual Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove repository contamination and refine the existing tablet UI without changing navigation or gestures.

**Architecture:** Keep the current screens and Material 3 theme, centralize a few spacing values, and make only measurable density/hierarchy changes. Treat build migration as an isolated, revertible experiment.

**Tech Stack:** Git, AGP 9.0.1, Kotlin 2.3.20, Jetpack Compose Material 3

**Spec:** `docs/superpowers/specs/2026-08-23-quality-foundation-design.md`

## Global Constraints

- Do not change control meaning, order of navigation destinations, or gesture assignments.
- Keep touch targets at least 48 dp even when reducing visual padding.
- Preserve the Windows repair backup outside the repository before removing tracked copies.

---

### Task 1: Repository hygiene

**Files:**
- Delete: `tools/windows-repair/step1-disable-wondershare.ps1`
- Delete: `tools/windows-repair/step2-repair-windows.ps1`
- Delete: `windows-repair-backup/*`
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `PROJECT_BRIEF.md`

- [ ] **Step 1: Copy the repair backup to `C:\Users\13326\Desktop\windows-repair-backup` and verify hashes/file count.**
- [ ] **Step 2: Remove tracked machine-specific files and ignore their project-local names.**
- [ ] **Step 3: Update stale documentation to the current gallery/player state and verification commands.**
- [ ] **Step 4: Run `git diff --check` and commit `chore: remove machine-specific repair artifacts`.**

### Task 2: Conservative gallery and player visual hierarchy

**Files:**
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/VideoGridScreen.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/HomeScreen.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/PlayerControls.kt`
- Modify: `app/src/main/java/com/pigfarmerjc/galleryplayer/GalleryTheme.kt`
- Test: `app/src/test/java/com/pigfarmerjc/galleryplayer/GalleryLayoutTest.kt`

- [ ] **Step 1: Add failing pure layout assertions for compact toolbar/bottom inset sizing and 2–12 column constraints.**
- [ ] **Step 2: Implement compact visual spacing while retaining 48 dp hit areas and existing action order.**
- [ ] **Step 3: Build/install, capture gallery and player-control screenshots, and compare against the baseline.**
- [ ] **Step 4: Run a 12-swipe FrameTimeline check to reject changes that worsen modern jank beyond measurement noise.**
- [ ] **Step 5: Commit `style: refine tablet gallery and player hierarchy`.**

### Task 3: Build configuration cleanup

**Files:**
- Modify: `gradle.properties`
- Modify: module `build.gradle.kts` files only if required by built-in Kotlin
- Modify: `core-storage/src/main/AndroidManifest.xml`

- [ ] **Step 1: Remove the obsolete manifest package attribute and run manifest processing.**
- [ ] **Step 2: In a single experiment remove deprecated Kotlin compatibility flags/plugins following AGP 9 diagnostics.**
- [ ] **Step 3: Run clean assemble, all JVM tests, KSP/Room tasks, and lint. Keep the migration only if all pass; otherwise restore only the Kotlin migration files.**
- [ ] **Step 4: Commit `build: clean up AGP 9 configuration` with only verified changes.**

### Task 4: Final device acceptance

- [ ] **Step 1: Run clean assembleDebug, every JVM unit test, lintDebug, and all connected app/database tests.**
- [ ] **Step 2: Install the final APK and run the complete interaction checklist from the design.**
- [ ] **Step 3: Record final cold start, grid scroll, memory, and crash/ANR evidence.**
- [ ] **Step 4: Review git diff, commits, repository status, and APK path before handoff.**

