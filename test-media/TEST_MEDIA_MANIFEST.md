# Test Media Manifest

This manifest documents the test media files used to verify `GalleryPlayer` video and audio playback capabilities.

| 文件 ID (File ID) | 容器 (Container) | 视频编码 (Video Codec) | 音频编码 (Audio Codec) | PCM 格式 (PCM Format) | 采样率 (Sample Rate) | 声道 (Channels) | 分辨率 (Resolution) | 帧率 (Frame Rate) | 大小 (Size) | 预期结果 (Expected Result) | 模拟器结果 (Simulator Result) | 真机结果 (Real Device Result) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TM-01 | MP4 | H.264 | AAC | - | 44.1 kHz | 2 (Stereo) | 1920x1080 | 30 fps | ~10 MB | 播放正常，声画同步 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-02 | MP4 | H.264 | AAC | - | 44.1 kHz | 2 (Stereo) | 3840x2160 | 60 fps | ~50 MB | 播放正常，高帧率无卡顿 | 成功 (依赖宿主CPU解码能力) | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-03 | MKV | H.265 | AAC | - | 48.0 kHz | 6 (5.1ch) | 3840x2160 | 60 fps | ~60 MB | 播放正常，HDR颜色显示正确 | 成功 (可能存在轻微掉帧) | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-04 | MOV | - | PCM S24 LE | S24LE | 48.0 kHz | 2 (Stereo) | - | - | ~20 MB | 音频正常出声，无爆音、无杂音 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-05 | MKV | - | PCM S24 LE | S24LE | 96.0 kHz | 2 (Stereo) | - | - | ~30 MB | 音频正常出声，无爆音、无杂音 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-06 | AVI | - | PCM S24 LE | S24LE | 44.1 kHz | 2 (Stereo) | - | - | ~15 MB | 音频正常出声，无爆音、无杂音 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-07 | WAV | - | PCM S24 LE | S24LE | 192.0 kHz | 2 (Stereo) | - | - | ~40 MB | 音频正常出声，极高采样率下正常 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-08 | MP4 | H.264 | AAC | - | 44.1 kHz | 2 (Stereo) | 1080x1920 | 30 fps | ~8 MB | 自动计算旋转，显示为竖屏视频 | 成功 (检测旋转90°) | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-09 | MP4 | - | - | - | - | - | - | - | ~2 MB | 提示文件损坏或不支持格式 | 成功 (抛出错误 state) | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-10 | MP4 | H.264 | - | - | - | - | 1920x1080 | 30 fps | ~5 MB | 正常播放无声，不闪退 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
| TM-11 | MP3 | - | MP3 | - | 44.1 kHz | 2 (Stereo) | - | - | ~3 MB | 正常播放仅声音，显示音频诊断 | 成功 | 真机兼容性验收：BLOCKED，等待目标设备连接 |
