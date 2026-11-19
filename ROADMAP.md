# Libre Launcher Roadmap

Architecture and feature roadmap for Libre Launcher.

---

## Releases

### v1.50 — UI Modernization & Social Ecosystem
- **Social Librepack Sharing**: Export and share curated modpack bundles directly with metadata and dependencies.
- **Material 3 Quick Settings**: In-game drawer replaced with a Copper Material 3 slide-out side sheet for fast renderer and control adjustments.
- **Dynamic Play-Time Widget**: Multi-size Android home screen app widget displaying player head, recent instance, and session duration.

### v1.60 — Auth Hardening & Driver Isolation
- **Microsoft Authentication Only**: Completely stripped offline and local login paths; launches require valid Microsoft tokens.
- **Per-Instance Hardware Profiles**: Isolated Turnip driver selection, snapshot-aware Libre Mobile renderer fallbacks, Rust control mapping, and custom JVM arguments per instance.
- **Mesa Diagnostics & Cache Cleaner**: One-tap Mesa and Libre shader cache cleaner plus `TU_DEBUG` (`sysmem`, `nobin`) runtime toggles in Custom Drivers.
- **Real SoC Model in F3 Debug**: Linux `/proc/cpuinfo` emulation passing real device chipsets (Snapdragon, Dimensity, Tensor, Exynos) into OSHI instead of `<unknown>`.

### v1.70 — Low-Latency Input & Controller Bridge
- **Rust TouchController Mod Bridge**: Direct native IPC channel bridging `libre_controls` events into in-game Fabric and NeoForge touch controller mods.
- **Raw USB Mouse PointerCapture**: Native `PointerCapture` implementation with full Mouse4 and Mouse5 button mapping for external mice.

### v1.80 — Native Modrinth Distribution & 26.4 Snapshot Engine
- **Built-in Rust Modrinth Client**: Native, asynchronous client querying Modrinth APIs for mods, resource packs, and shaders.
- **`mrpack` Streaming Installer**: Direct modpack installation with concurrent chunk downloading and SHA-512 cryptographic verification.
- **Libre Mobile 26.4 Snapshot Engine**: Full Core GLSL 460 compatibility, mid-shader extension directives, and core GL extensions for modern 26.4 snapshots.

### v2.00 — Flagship Core & Full Material 3 Architecture
- **Unified Rust Frame Pacing & Input Core**: Sub-millisecond touch sampling, low-latency ring buffer, zero-jitter frame timing, and unified JNI telemetry dispatch.
- **Thermal-Aware Hibernate Profiles**: Dynamic governor reading Android battery temperatures and SoC thermal status to scale down FPS, surface resolution, and polling when the device heats up.
- **100% Material 3 Copper Architecture**: Full audit eliminating all legacy dialogs and popups in favor of unified Material 3 Copper styling across the entire launcher.
- **Pre-Boot Mod Conflict Analyzer**: Fast static analysis scanning JAR manifests (fabric.mod.json and quilt.mod.json) for duplicate mod IDs, missing dependencies, incompatible x86 native libraries, and Android renderer conflicts with a Material 3 one-tap fix dialog.
- **Dynamic Launch Shortcuts**: Pin Minecraft instances directly to the Android home screen launcher to launch any instance with one tap.

### v2.30 — OpenJDK 21 Android 16 & F-Droid Pure Source Compliance
- **100% F-Droid Source Compliance**: Compiled `libfreetype.so` from source submodule without prebuilt binaries or non-free blobs.
- **Android 16 (API 36) Linker Namespace Isolation Fix**: Physical copying of native runtime libraries (`libfreetype.so`, `libawt_xawt.so`, `libgl4es_114.so`) into runtime directory with explicit execution permissions, resolving `clns-9` classloader namespace isolation failures.
- **OpenJDK 18 + 21 Compatibility**: Flawless booting of Minecraft 1.21.x with modern Java runtimes on Android 16.

### v2.33 — Precision Controls & Native Telemetry Uncap
- **Pinpoint Touch-Click Accuracy**: Replaced legacy screen-space mapping with surface-local coordinates dynamically scaled to active Minecraft framebuffer dimensions (`windowWidth` / `windowHeight` / view dimensions) on orientation and layout updates.
- **Virtual Control Event Isolation**: All virtual controls, buttons, drawers, and joysticks consume touch events completely (`return true`), preventing phantom clicks through to the underlying GUI canvas.
- **Direct Native Frame Swap Present Hook**: Unlocked telemetry FPS beyond 60 FPS cap by counting actual buffer swaps in native `eglSwapBuffers` using `CLOCK_MONOTONIC` (`Instant`) rather than Android UI `Choreographer` ticks.
- **120Hz+ Max Display Refresh Rate & Frame Rate Unlocking**: Automatic selection of highest supported `Display.Mode` refresh rate and unlocked surface frame rate (`setFrameRate(0f)`).
- **New Custom Libre Controlmap**: Replaced legacy 2021 Pojav default layout with modern custom Libre Launcher controlmap with zero legacy references.
- **F3 SoC Hardware Emulation**: Full SoC chipset reporting (Qualcomm Snapdragon, MediaTek Dimensity, Google Tensor, Samsung Exynos) into Minecraft F3 screen.

### v2.35 — Integrated Auto-Updater & In-App Release Installer
- **Automatic Update Checking**: Seamless background check querying GitHub Releases API on startup (`PolskieWin/libre-launcher`) with session caching and manual check support in Settings -> Miscellaneous.
- **Material 3 Update Dialog**: Interactive dialog displaying the new version, current version, and formatted release changelog with one-tap "Update", "GitHub", or "Later" actions.
- **In-App Download & Streamed Progress**: Direct HTTP asset downloading with 30x redirect following, real-time download progress tracking (MB downloaded / total size and percentage), and determinate progress bar.
- **Secure FileProvider APK Installation**: Seamless transition to the Android system package installer via `androidx.core.content.FileProvider` and `REQUEST_INSTALL_PACKAGES` permission management with unknown-sources recovery.

### v2.36 — Unlocked Telemetry & Dual-Path Real-Time Frame Metrics
- **Telemetry Frame Pacing Restoration**: Restored `Choreographer.FrameCallback` on `TelemetryOverlayView` to guarantee frame pacing up to 120Hz/144Hz.
- **Dual-Path Telemetry Pipeline**: Integrated native present precedence with 250ms monotonic timeout window, enabling automatic fallback to VSYNC display frame pacing when native EGL swap buffers is not directly linked.
- **Dynamic 16KB Page-Size RAM Metrics**: Upgraded memory reader to dynamically query `sysconf(_SC_PAGESIZE)` and inspect `/proc/self/status` (`VmRSS`) with JVM runtime memory fallback.
- **Immediate Telemetry Snapshot Dispatch**: Return populated telemetry buffers immediately upon activation instead of returning empty zero values before 120 frames have accumulated.

### v2.37 — Hardened Auto-Updater Pipeline & Build Reliability
- **Hardened Background Update Pipeline**: Wrapped all directory setup and APK cache purging within atomic exception guards in `UpdateManager` to guarantee complete IO safety across all Android versions.
- **Strict Compiler Exception Compliance**: Resolved unhandled `IOException` bounds on executor threads during automated update download handshakes.
- **Seamless Continuous Integration**: Verified flawless compilation and packaging across `full`, `noruntime`, and `stripped` build variants.

### v2.38 — Forge & NeoForge Parity, 26.4 Snapshots & LibreMobile 2.00 F3
- **Forge & NeoForge Stability & Inheritance**: Complete JVM argument and library inheritance merging without duplicates, full support for rule-based JVM arguments (`--add-opens`, `--add-modules`, system properties), automatic NeoForge Minecraft version detection (`neoforge-` prefix to MC version resolution), and Forge terminal/AWT workarounds (`-Dneoforge.earlyprogresswindow=false`, `-Dforge.forceNoStencil=true`, `-Dterminal.jline=false`, `-Djline.terminal=none`).
- **Resilient Forge/NeoForge Installer Automation**: Case-insensitive and HTML-stripped button normalization supporting "Install client", "Install", "OK", "Proceed", and non-blocking dialog inspection handling all `JOptionPane` variants.
- **Libre Mobile 2.00 in F3 Debug Screen**: Intercepted LWJGL `DynamicLinkLoader` and EGL proc address queries to report `"Libremobile 2.00"` on F3 debug screen instead of raw `turnip`, `zink`, or `mesa git`.
- **26.4 Snapshot Engine & Core GLSL Extensions**: Full compatibility with 26.4 snapshots via core GLSL 460 compat mode, compute shader / SSBO overrides, snapshot regex sorting in `ComparableVersionString`, and automatic Java 21 runtime resolution.
- **Pre-Boot Mod Compatibility for Forge & NeoForge**: Native Rust (`libre_controls`) and C (`preboot_scanner`) support for `mods.toml` and `neoforge.mods.toml` mod manifest inspection, detecting duplicate mod IDs, missing dependencies, and renderer conflicts across Forge and NeoForge.

### v2.40 — Anti-Piracy Enforcement, Microsoft Auth Hardening & F-Droid Pure Compliance
- **Complete Anti-Piracy Enforcement**: Decommissioned offline/cracked local session fallback (`account.toLocal()`) in `GameRunner`. All game launches strictly mandate authenticated Microsoft accounts with active tokens.
- **Loopback Auth Emulation Removal**: Decommissioned `LocalSkinServer` Yggdrasil-compatible loopback HTTP server and stripped unofficial Ely.by authorization backend.
- **Javaagent Security Neutralization**: Completely removed `authlib-injector.jar` and `NoBlocklist-1.0-SNAPSHOT-all.jar` JVM agent hooks, excised `NoBlocklist` submodule, and removed the server blocklist bypass toggle in Preferences to enforce Mojang's official blocked servers policy.
- **F-Droid Build Compliance**: Removed legacy builds (`2.34` - `2.38`) containing cracked piracy vectors from F-Droid metadata recipes, eliminated `authlib-injector` from `srclibs` and `prebuild`, and flavor-gated in-app `UpdateManager` for `noruntime` package-managed builds.
- **Ultra-Lightweight Stripped Flavor**: Dedicated standalone APK flavor containing 2 essential renderers (Libre Mobile + GL4ES) and zero bundled runtimes (~35–45 MB), tailored for GitHub Releases and IzzyOnDroid.

### v2.50 — F-Droid Compliance, Offline Noruntime Flavor & Version Metadata Alignment
- **Strict F-Droid Offline Build Compliance**: Gated dynamic JitPack JAR dependency retrieval during `:app_mojo` configuration for `noruntime` flavor, enforcing 100% compilation from source libraries (`caciocavallo`, `pro-grade`, `MobileGlues`, `mesa`, `gl4es`, `freetype`) in F-Droid's isolated offline environment.
- **Exact Version Name Identity**: Removed `-noruntime` versionNameSuffix on the `noruntime` build flavor, guaranteeing bit-for-bit package metadata alignment (`versionName: '2.50'`, `versionCode: 250`) with F-Droid build manifests.
- **Complete Fastlane Metadata & Visual Assets**: Updated full upstream Fastlane directory structure (`fastlane/metadata/android/en-US/`), including high-resolution app icon, localized summaries, and complete feature screenshots (`1_home.jpg`, `2_settings.jpg`, `3_skinChangerMenu.jpg`, `4_homeFolders.jpg`).
- **Unified Release Pipeline**: Synchronized release version tags and metadata recipes across GitHub and GitLab mirrors.

---

## Upcoming Milestones

### v2.60 — Adaptive Vulkan Pipeline & Official Repository Inclusion
- **F-Droid Official Repository Inclusion**: Completion and merge of official F-Droid Merge Request `!58168`.
- **Adaptive Vulkan Extension Optimizer**: Dynamic detection of Turnip driver extensions with real-time pipeline optimizations.
