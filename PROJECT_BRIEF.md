# PROJECT BRIEF - Handoff & Context Document

This document summarizes the background, goals, and current state of the GalleryPlayer project to preserve context across computer migration and fresh agent sessions.

---

## 1. Project Overview & Target
- **App Name**: GalleryPlayer
- **Form Factor**: Primarily designed for 8-inch Android tablets.
- **Goal**: A premium, everyday-use local video/image library and player.
- **Engine**: Core playback is powered by a LibVLC engine (`player-libvlc`).

---

## 2. Completed Milestones
- **Core Navigation**: Video Grid (16:9), Folders list view, Images grid, and Settings.
- **Playback History & Resuming**: Auto-saving playback locations, auto-resuming from history, and progress bar indicator overlays on video thumbnails.
- **Library Filtering & Sorting**: Persistent search text matching, file sorting (name, date, size, duration), and folder sorting (name, video count, size, date).
- **Home Hub Layout**: "Continue Watching" horizontal carousel showing recently played unfinished videos.
- **Player Interaction**: Double-tap to seek custom seconds, horizontal swipe to switch videos, swipe-down to dismiss player, status bar immersive toggling, speed controllers, and repeat modes (`NONE`, `ONE`, `ALL`).

---

## 3. Removable Storage & Hardware Constraints (Sprint 6B)
- **External Storage (TF Cards)**: Supported via MediaStore multi-volume scanning and manual SAF directory tree selection with persistent read permission tree URI storage.
- **4K Playback**: Avoids full-file buffer memory copy operations by playing directly from Content URIs. Selectable hardware decoder configurations (`Auto`, `Hardware forced`, `Software only`) are supported in settings.
- **PCM S24 LE Audio Tracks**: Direct native LibVLC delegation without codec constraints. Track info (channels, sample rate) is exposed in diagnostics on playback.

---

## 4. Diagnostics & Testing Checklist
Tonight's manual readiness verification procedure:
1. Install debug APK.
2. Grant video & image storage permissions.
3. Validate local internal storage video scanning.
4. Mount a TF card.
5. Tap Settings -> Re-scan Media.
6. If TF card media does not appear, tap Settings -> Select TF Card Folder, grant access to the TF card root, and check media listing.
7. Attempt playback of a standard video, a 4K video, and a PCM S24 LE audio track video.
8. Switch decoder modes in Settings (Auto / Hardware forced / Software) and test playback difference.
9. Open Settings -> Diagnostics Console, verify stats, click "Copy Diagnostics", and paste logs back for developer feedback.
