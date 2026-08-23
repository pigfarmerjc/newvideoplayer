# GalleryPlayer (新一代媒体播放器) 项目说明书与上手指南

> [!NOTE]
> **Current direction: Product Reset MVP**
> The app is now focused on becoming a usable local video player first.
> Architecture experiments and advanced scanner work are secondary.

---

## 一、 产品说明 (我让你做什么 & 我怎么理解的)

### 1. 核心产品目标
开发一款本地多媒体播放器，支持视频与音频的快速导入、多维度存储卷/文件夹组织、历史记忆续播、倍速播放与高级解码配置。要求架构设计高度解耦，UI 层不依赖特定的播放底层（如 LibVLC），且能在后台生命周期内保证资源的正确回收。

### 2. 模块划分架构设计
项目在架构上完全推倒了紧密耦合的设计，划分为六个独立的模块：
*   **`:app` (主应用)**：只负责 Jetpack Compose 渲染与 UI 状态逻辑，通过 ViewModel 间接操控底层播放。
*   **`:core-player-api` (播放引擎接口)**：定义 `PlaybackEngine` 与 `VideoOutputHost` 接口，提供完全不含第三方库污染的底层接口标准。
*   **`:player-libvlc` (LibVLC 播放实现)**：封装 LibVLC，实现硬解强制、自动解码与软解的平滑切换，仅在该模块内依赖 LibVLC 动态链接库。
*   **`:core-database` (Room 数据库)**：本地数据持久化层，实现媒体库、播放历史、缩略图缓存等数据实体的维护与级联管理。
*   **`:core-model` (业务实体模型)**：纯 Kotlin 编写的未受 Room 标签污染的数据实体定义（如 `MediaItem`），实现层间数据传递的清洁性。
*   **`:core-common` (公共组件)**：核心协程调度器 `Dispatcher` 绑定，用于多模块间后台任务的快速切换。

---

## 二、当前实现状态

截至 2026-08-23，应用已经具备可在真实平板上日常使用的媒体库与播放器主链路：

* MediaStore 多存储卷增量扫描、Room 批量同步、文件夹与媒体库分类、搜索和排序。
* LibVLC 播放、历史续播、倍速、画面比例、音轨/字幕、收藏、解码模式与诊断信息。
* 播放器手势、上一条/下一条，以及“播完停止 / 单个循环 / 自动下一个”三种结束策略。
* 可自由拖动和缩放的应用悬浮小窗，自动适配横竖视频，并支持播放控制与切换视频。
* 缩略图请求去重和常数时间降级查找，降低大媒体库滚动时的重复解码和主线程压力。

下列早期阶段记录保留为架构背景：

### Phase 0: 核心可行性测试与项目骨架搭建
*   **解决 Gradle 构建痛点**：避开不兼容的旧 SDK 创建命令，手动编写搭建了完整的 Gradle Kotlin DSL 多模块架构，并配置统一的 Version Catalog 版本管理。
*   **消除进度条时间争抢 (Timeline Fight)**：拖拽进度条时，传统实现会导致滑动块反复弹跳。我们通过“解耦滑动态”设计，使滑动条在拖动中读取临时变量，松手时才向播放引擎发起 `seekTo` 事务，彻底解决画面进度抖动。
*   **旋屏状态持久化**：使用 Jetpack ViewModel 持有底层 `PlaybackEngine` 实例，旋转屏幕导致 Activity 重建时，画面渲染上下文能平滑绑定至新视图，而播放进度不发生中断。

### Phase 0.5: 架构安全性审计与隔离优化
*   **解耦 LibVLC 依赖泄露**：将 `:player-libvlc` 中 LibVLC 依赖的 `api` 作用域更改为 `implementation`，杜绝 `:app` 与 `:core-player-api` 发生任何 `org.videolan` 的类依赖泄露。
*   **Video 渲染上下文屏蔽**：封装 `VideoOutputHostFactory` 模式，在 UI 层仅向外暴露普通的 `View` 封装，具体的 `VLCVideoLayout` 在模块边界内隐藏。
*   **后台与垃圾回收安全性**：
    1.  监听 Compose 生命周期，在后台挂起 (`ON_PAUSE`) 或销毁时，将播放状态正确锁定，解决应用挂机依然漏音的 Bug。
    2.  对持久化 Uri 的 `ParcelFileDescriptor` 执行严格的 lifecycle 追踪管理，在销毁播放器或切歌时及时关闭文件描述符，防范操作系统层级的句柄泄露。

### Phase 1: 本地多媒体数据库与实体开发
*   **降伏 Kotlin 2.3.20 编译冲突**：
    - *问题*：本项目采用非常前沿的 Kotlin 2.3.20 编译器，它生成的类 metadata 版本为 `2.3.0`。而 Room 2.6.1 只能读懂最大为 `2.0.0` 的元数据，导致 kapt 注解处理器直接抛出 `IllegalArgumentException` 编译器崩溃。
    - *解决*：我们将 Room 数据库升级至 `2.8.4` 稳定版，原生兼容 Kotlin 2.x 编译器元数据，并禁用 `android.builtInKotlin` 旧 DSL 设置，确保所有库无报错一次性编译通过。
*   **库与级联机制设计**：
    - `MediaItemEntity` 设置复合索引与唯一性约束 `(volume_name, media_store_id)`，确保多存储卷（SD卡与内部存储）的数据唯一。
    - 针对 `PlaybackHistoryEntity` (历史) 与 `ThumbnailCacheEntity` (缩略图)，配置了**外键级联删除 (ForeignKey.CASCADE)** 规则。一旦数据库删除了父级 `MediaItem`，对应的历史及缓存记录将被底层 SQLite 引擎自动销毁，避免产生垃圾脏数据。
*   **测试套件验证**：在 `core-database/src/androidTest` 下编写了测试用例，并在 AVD 模拟器上跑通了增删改查、批量覆盖事务、级联清理等行为，测试 100% 通过。

---

## 三、后续优化方向

* 建立覆盖不同编码、分辨率和异常文件的固定兼容性样本集。
* 持续采集长时间播放、4K/HEVC 和大媒体库滚动性能数据。
* 完善悬浮窗位置与大小持久化、播放队列管理和无障碍体验。
* 逐步迁移 Android Gradle Plugin 的内建 Kotlin DSL，消除构建期弃用警告。

---

## 四、 新电脑上手与编译运行指南 (让我在新电脑上down下来就能接着做)

如果你在一台新电脑上拉下了本项目，请按照以下步骤配置运行：

### 1. 基础依赖环境
*   **操作系统**：Windows / macOS / Linux 均可。
*   **JDK 版本**：必须使用 **JDK 17** 作为项目的 Gradle 编译工具链（Gradle wrapper 已锁定 Java 17 兼容性）。
*   **Android SDK**：
    - SDK API Level: 需下载 **Android 15 (API 35/36)** 对应的 SDK 工具及平台。
    - AVD 模拟器：推荐创建基于 Android 15 (x86_64 CPU ABI) 的模拟器（如 `Pixel_Tablet`）。

### 2. 常用命令行指南 (在项目根目录下执行)
打开终端（Windows 推荐使用 Powershell）：

*   **清理与编译项目**
    ```powershell
    .\gradlew.bat clean assembleDebug
    ```
    *说明*：这会执行全局清空并重新编译出 Debug 版本的 APK，成功后安装包将位于 `app/build/outputs/apk/debug/app-debug.apk`。

*   **查看模块依赖网与注册情况**
    ```powershell
    .\gradlew.bat projects
    ```

*   **运行数据库单元与集成测试 (必须打开 AVD 模拟器)**
    ```powershell
    .\gradlew.bat :core-database:connectedDebugAndroidTest
    ```
    *说明*：这会在运行的模拟器上启动 SQLite 测试套件，自动运行插入、删除、级联清理并打出测试通过报告。

*   **运行播放引擎与 UI 冒烟集成测试 (必须打开 AVD 模拟器)**
    ```powershell
    .\gradlew.bat :app:connectedDebugAndroidTest
    ```
    *说明*：这会在模拟器上安装集成测试包，调用 LibVLC 播放一段 H.264 视频，并进行时长校验、二倍速切换、手势滑动模拟、锁屏暂停等状态断言。

### 3. 打开项目
1.  启动 Android Studio。
2.  点击 **Open**，选择本项目根目录（包含 `settings.gradle.kts` 的文件夹）。
3.  等待 Gradle 自动 Sync 与依赖项下载。Sync 结束后，可以直接在 Android Studio 的右上角选择 `app`，并在目标模拟器上点击 Run 运行主应用。
