<h1 align="center">Libre Launcher</h1>

<p align="center">
  <img src="./app_mojo/src/main/ic_launcher-playstore.png" width="128" height="128" alt="Libre Launcher logo"><br>
  <b>A lightweight, high-performance Minecraft: Java Edition launcher for Android</b><br>
  <i>Forked from Copper Launcher, powered by Rust native controls and full Minecraft 26.3 & 26.4 support.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-brightgreen" alt="Platform">
  <img src="https://img.shields.io/badge/engine-Rust%20%2B%20Java%20%2F%20C-orange" alt="Engine">
  <img src="https://img.shields.io/badge/license-LGPLv3-blue" alt="License">
  <img src="https://img.shields.io/badge/Minecraft-rd--132211%20to%2026.4-green" alt="Minecraft Versions">
  <a href="ROADMAP.md"><img src="https://img.shields.io/badge/Roadmap-v1.50--v2.00-blueviolet" alt="Roadmap"></a>
  <a href="https://discord.gg/gdcm"><img src="https://img.shields.io/badge/Discord-Join%20Community-5865F2?logo=discord&logoColor=white" alt="Discord"></a>
</p>

---

## Roadmap

See [ROADMAP.md](ROADMAP.md) for full version milestones and development plans through v2.00.

## Key Improvements in Libre Launcher

- **Rust Native Controls (`libre_controls`)**:
  - Rebuilt virtual touch & motion tracking pipeline in Rust.
  - Zero heap allocation during gameplay — eliminates garbage collection lag spikes.
  - Non-interrupted sprint transitions when turning with virtual joystick (diagonal-to-cardinal key debounce).
  - Exponential Moving Average (EMA) aim smoothing to remove touchscreen jitter.
- **Minecraft 26.3 & 26.4 Ready**:
  - Full support for 2026 calendar versioning (`26.1`, `26.2`, `26.3`, `26.4`, and `26w...` snapshots).
  - Fixed NeoForge 4-part version resolution (`26.x.y.z`) preventing version parsing crashes.
  - Snapshot comparator contract fix in `ComparableVersionString` preventing TimSort crash bugs.
  - Smart automatic JRE runtime fallback (Java 21/25 LTS for modern 26.x versions).
- **Unified Rust Frame Pacing & Thermal-Aware Hibernate AFK**:
  - Unified Rust native engine via JNI providing sub-millisecond input dispatch and lock-free ring-buffer frame pacing.
  - Thermal governor reading Android battery temperatures and SoC thermal status to scale down FPS, surface resolution (0.85x–0.50x), and background polling when device heats up.
  - OLED pure black Hibernate mode (5 FPS, 0.25x resolution) for minimal battery usage during AFK sessions.
- **100% Material 3 Copper Architecture**:
  - Consistent Material 3 theme across all launcher activities, dialogs, drawers, and in-game dialogs with zero legacy popups.
- **Native Pre-Boot Mod Compatibility Scanner**:
  - Fast native Rust/JNI analyzer scanning `fabric.mod.json`, `quilt.mod.json`, and bundled ELF native binaries before JVM launch.
  - Automatically identifies duplicate mod IDs, missing dependencies, x86-only native libraries on ARM Android, and renderer conflicts (e.g., Sodium on GL4ES, VulkanMod without Turnip).
  - Clean Material 3 warning dialog with one-tap automatic fix or launch anyway.
- **Direct Pinned Home Screen Shortcuts**:
  - Pin any Minecraft instance directly to the Android home screen for instant one-tap launching.
- **Advanced Custom Driver & Renderer Management**:
  - Direct access to Custom Vulkan Drivers (Turnip / Freedreno) in Video Settings without arbitrary UI hiding.
  - Full renderer suite: GL4ES, Mesa Zink (OpenGL over Vulkan), OpenLTW, ANGLE, VirGL, and plugin renderers.
- **Lightweight & Debloated**:
  - Stripped unnecessary binder IPC logcat chatter during touch inputs.
  - Cleaned up obsolete legacy logging tags.
  - Optimized View touch event propagation to minimize Android UI traversal overhead.

---

## Building

### Android APK Build via Gradle

```bash
# Clone the repository with submodules
git clone --recurse-submodules https://github.com/PolskieWin/libre-launcher.git
cd libre-launcher

# Build Debug APK
./gradlew :app_mojo:assembleFullDebug
```

The resulting APK will be located at:
`app_mojo/build/outputs/apk/full/debug/app_mojo-full-debug.apk`

### Building the Rust Controls Library

```bash
cd libre_controls
cargo build --release
cargo test
```

---

## Credits & Lineage

Libre Launcher is built upon the incredible work of the open-source Minecraft on mobile community:
- **[Copper Launcher](https://github.com/CopperLauncher/Copper-Android)** by MaxJubayerYT
- **[MojoLauncher](https://github.com/MojoLauncher/MojoLauncher)**
- **[Amethyst Launcher](https://github.com/AngelAuraMC/Amethyst-Android)**
- **[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)**

Licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.

---

## Legal & Trademarks

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Minecraft is a registered trademark of Mojang AB. Libre Launcher is an independent open-source launcher developed under the LGPL-3.0 license and is not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft Corporation. All trademarks belong to their respective owners.
