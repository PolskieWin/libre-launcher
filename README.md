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

See [ROADMAP.md](ROADMAP.md) for full version milestones and development plans through v2.40.

## Key Improvements in Libre Launcher

- **100% F-Droid Source Compliance & Zero Blobs**:
  - Full F-Droid main catalog compliance (`!58168`): all native dependencies including `libfreetype.so` are compiled directly from source submodules. Zero prebuilt blobs or proprietary components.
- **OpenJDK 18 + 21 Compatibility on Android 16 (API 36)**:
  - Advanced linker namespace isolation workaround: copies runtime native libraries into the execution namespace with explicit permissions, resolving `clns-9` classloader isolation failures on Android 16.
- **Rust Native Controls (`libre_controls`) & Custom Libre Controlmap**:
  - Brand-new custom Libre Launcher controlmap replacing legacy 2021 layouts with rounded aesthetic controls.
  - Pinpoint 1:1 touch-click accuracy: surface-local coordinates dynamically scaled to active Minecraft framebuffer dimensions across orientation and resolution scaling.
  - Event isolation: virtual controls and buttons fully consume touch events (`return true`), eliminating phantom clicks through to the game canvas.
  - Non-interrupted sprint transitions with diagonal-to-cardinal debounce and EMA aim smoothing.
- **Native Frame Swap Present Hook & Unlocked 120Hz+ Telemetry**:
  - Frame counting directly inside native `eglSwapBuffers` using monotonic `Instant` (`CLOCK_MONOTONIC`) without Android UI `Choreographer` 60 FPS caps.
  - Automatic maximum display refresh rate selection (`preferredDisplayModeId` for 120Hz/144Hz) and unlocked game surface frame rate (`setFrameRate(0f)`).
- **Thermal-Aware Hibernate AFK & Real SoC F3 Info**:
  - Real-time battery and SoC thermal governor dynamically scaling down FPS, surface resolution (0.85x–0.25x), and polling when hot.
  - Pure AMOLED black Hibernate mode (5 FPS) for long AFK sessions.
  - Real SoC model detection (Snapdragon, Dimensity, Tensor, Exynos) in Minecraft F3 debug screen.
- **Minecraft 26.3 & 26.4 Ready**:
  - Full support for 2026 calendar versioning (`26.1`, `26.2`, `26.3`, `26.4`, and `26w...` snapshots).
  - Fixed NeoForge 4-part version resolution (`26.x.y.z`) preventing version parsing crashes.
  - Snapshot comparator contract fix in `ComparableVersionString` preventing TimSort crash bugs.
  - Smart automatic JRE runtime fallback (Java 21/25 LTS for modern 26.x versions).
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
- **Upcoming Stripped Flavor**:
  - Standalone ultra-lightweight `stripped` APK flavor (~35–45 MB) containing 2 essential renderers (Libre Mobile + GL4ES) and zero bundled runtimes for GitHub Releases and IzzyOnDroid.

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
