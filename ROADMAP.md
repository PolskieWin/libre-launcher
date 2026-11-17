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

### v1.90 — Compatibility Diagnostics & Direct Shortcuts
- **Pre-Boot Mod Conflict Analyzer**: Fast static analysis scanning JAR manifests for incompatible mixins, duplicate libraries, or known renderer conflicts before JVM launch.
- **Dynamic Launch Shortcuts**: Pin specific game instances directly to the Android home screen launcher.

### v2.00 — Next-Gen Core & Full M3 Architecture
- **Unified Rust Frame Pacing & Input Core**: Merged sub-millisecond touch sampling, Vulkan swapchain synchronization, and zero-jitter frame timing.
- **Thermal-Aware Hibernate Profiles**: Adaptive governor throttling background work to mitigate thermal throttling during long sessions.
- **100% Material 3 Copper Architecture**: Complete conversion of all remaining legacy dialogs, lists, and editors to unified Material 3 Copper styling.
