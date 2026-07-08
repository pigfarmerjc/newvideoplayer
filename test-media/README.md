# Test Media Directory

This directory is designated for local testing of audio and video formats, especially for the verification of `GalleryPlayer` capabilities on simulators and real devices.

## Rules
1. **DO NOT commit actual media files to Git.** Large files are excluded via `.gitignore`.
2. Keep only the manifest (`TEST_MEDIA_MANIFEST.md`) and this `README.md` tracked in source control.

## Preparing Test Media
You can use standard test files or synthesize small media samples using `FFmpeg`.

### Synthesis Command Examples:

#### 1. Generate 1080p H.264 + AAC MP4
```bash
ffmpeg -f lavfi -i testsrc=duration=10:size=1920x1080:rate=30 -f lavfi -i sine=frequency=1000:duration=10 -c:v libx264 -c:a aac tm-01.mp4
```

#### 2. Generate MOV containing PCM S24 LE Audio
```bash
ffmpeg -f lavfi -i testsrc=duration=10:size=1920x1080:rate=30 -f lavfi -i sine=frequency=1000:duration=10 -c:v mpeg4 -c:a pcm_s24le tm-04.mov
```

#### 3. Generate WAV with PCM S24 LE Audio
```bash
ffmpeg -f lavfi -i sine=frequency=440:duration=10:sample_rate=48000 -c:a pcm_s24le tm-07.wav
```
