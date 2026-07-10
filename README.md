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

## 二、 开发实录 (我怎么做的)

整个项目按阶段推进，截至目前已经顺利完成了 **Phase 0**、**Phase 0.5** 与 **Phase 1**：

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

## 三、 未完工部分 (还有什么没做的)

要在新电脑上拉取代码继续推进，后续的任务排期如下（即下一阶段的开发内容）：

*   **Phase 2: 媒体扫描器与同步服务 (File Scanner & Media Sync)**
    - *目标*：实现一套后台扫描机制，检索设备上的媒体文件。
    - *逻辑*：读取 `MediaStore` 提供的媒体行指针，增量转换为 `:core-model` 的 `MediaItem`，调用 `:core-database` 的 Repository 执行 `upsertAll` 批量写入，自动过滤重复项。
*   **Phase 3: 播放列表与多媒体控制管理 (Playlist & State Manager)**
    - *目标*：设计播放列表切换逻辑（单曲循环、顺序播放、列表循环）。
    - *逻辑*：在播放新媒体时自动检索 `PlaybackHistoryEntity` 的历史进度，若存在未播放完的历史记录，则提示用户并跳转记忆时间点。
*   **Phase 4: 媒体库高级 UI 与交互设计**
    - *目标*：重构列表展示，按照文件夹、音视频分类，并异步加载视频帧缩略图。
    - *交互*：在播放器页面加入手势操作控制（左侧滑动调节亮度，右侧滑动调节音量，双击暂停/播放）。
*   **Phase 5: 解码优化与兼容性适配**
    - *目标*：对于高 bit rate 的 4K / HEVC 视频硬解码参数微调，优化在低配置手机上的掉帧率，并编写在真实设备上的 PCM S24 LE 格式测试。

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
