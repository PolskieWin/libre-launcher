# F-Droid & IzzyOnDroid Inclusion Guide

This document details the compliance architecture, anti-features disclosure, and exact submission procedures for publishing **Libre Launcher** to F-Droid ecosystem repositories (both IzzyOnDroid and the official F-Droid Main Repository).

---

## 1. Compliance Audit & Summary of Changes

To comply with F-Droid and IzzyOnDroid inclusion policies, the following requirements have been implemented:

| Requirement | Status | Implementation Details |
|---|---|---|
| **Free / Libre License** | Compliant | Licensed under **GNU LGPL v3.0** (`LICENSE`). |
| **No Proprietary Tracking / Ads** | Compliant | Zero analytics, zero Firebase, zero Crashlytics, zero advertising SDKs (verified 0 trackers via Exodus Privacy). |
| **No Prebuilt Binary Blobs in Maven** | Compliant | Removed `gson-2.8.6.jar`, `ExagearApacheCommons.jar`, and `exp4j-0.4.9-SNAPSHOT.jar` from `app_mojo/libs/`. Replaced with official Maven Central dependencies (`gson:2.10.1`, `commons-io:2.15.1`, `commons-compress:1.26.0`, `commons-lang3:3.14.0`, `exp4j:0.4.8`). |
| **No Copyright-Infringing Assets** | Compliant | Removed unreferenced proprietary `minecraft_ten.ttf` font containing Mojang AB copyright headers from resources. |
| **Trademark Disclaimers** | Compliant | Added mandatory Mojang/Microsoft trademark disclaimer across `README.md`, `strings.xml`, `wiki_dialog_content`, and Fastlane metadata. |
| **Fastlane Metadata Structure** | Compliant | Standard Fastlane structure generated at `fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`, and 512x512 `icon.png`. |
| **Deterministic Versioning** | Compliant | Updated `app_mojo/build.gradle` to support `-PversionCode` and `-PversionName` properties, environment variables, git commit counts, and semantic tags instead of hardcoded fallback numbers. |
| **Reproducible No-Runtime Flavor** | Compliant | `noruntime` flavor (`assembleNoruntimeRelease`) does not trigger arbitrary zip downloads during Gradle build execution. |

---

## 2. Anti-Features Disclosure

Because Libre Launcher interacts with Microsoft authentication services and downloads Minecraft: Java Edition game components, F-Droid requires declaring the following **Anti-Features**:

1. **`NonFreeNet`** (Non-Free Network Services):
   - **Reason**: The application connects to Microsoft / Mojang authentication endpoints (`login.live.com`, `api.minecraftservices.com`) and downloads official game assets from Mojang/Microsoft content delivery networks.
   - **F-Droid Policy**: Allowed, provided the application itself is 100% open-source and the anti-feature is explicitly flagged in the metadata.
2. **`NonFreeDep`** (Non-Free Dependencies):
   - **Reason**: Running Minecraft: Java Edition requires official game files and a legitimate Microsoft/Mojang account.

---

## 3. Submission Path A: IzzyOnDroid (Recommended & Standard for Launchers)

**IzzyOnDroid** is the official F-Droid third-party repository designed specifically for open-source applications that include complex C++/Rust/NDK components or download external runtimes. IzzyOnDroid tracks GitHub Releases directly and distributes verified APKs to the entire F-Droid userbase.

### Submission Steps:

1. **Push commits & tag a release** on GitHub:
   ```bash
   git tag -a v2.34 -m "Release v2.34"
   git push origin v2.34
   ```
2. Ensure GitHub Actions successfully runs `.github/workflows/android.yml` and attaches:
   - `LibreLauncher-release.apk`
   - `LibreLauncher-noruntime-release.apk`
   - `LibreLauncher-stripped-release.apk`
3. Open an issue at [IzzyOnDroid Repository Issues](https://gitlab.com/IzzyOnDroid/repo/-/issues/new?issuable_template=Inclusion).
4. Use the pre-filled template below.

### IzzyOnDroid Issue Template (Copy & Paste):

```markdown
### General Information
* Application Name: Libre Launcher
* Package ID: org.libre.launcher
* License: LGPL-3.0-or-later
* Source Code: https://github.com/PolskieWin/libre-launcher
* Releases: https://github.com/PolskieWin/libre-launcher/releases
* Issue Tracker: https://github.com/PolskieWin/libre-launcher/issues

### Summary & Description
* Summary: Fast Minecraft: Java Edition launcher with native Rust controls
* Description: Lightweight, high-performance Minecraft: Java Edition launcher for Android featuring native Rust touch controls, thermal-aware AFK hibernate mode, pre-boot mod compatibility scanning, and complete Material 3 design.

### Fastlane & Metadata
* Fastlane metadata available in repository: Yes (`fastlane/metadata/android/en-US/`)
* App Icon: `fastlane/metadata/android/en-US/images/icon.png`

### Anti-Features
* NonFreeNet: Connects to Microsoft/Mojang authentication servers to download official Minecraft assets.
* NonFreeDep: Requires valid Minecraft: Java Edition account and game files to play.

### Trackers & Privacy
* Exodus Privacy: 0 trackers detected.
* Advertising / Telemetry: None.

### APK & Build
* Release APK is attached to GitHub releases: Yes (`LibreLauncher-release.apk` and `LibreLauncher-noruntime-release.apk`).
```

---

## 4. Submission Path B: F-Droid Main Repository (`fdroiddata`)

To submit directly to the official F-Droid build server repository:

1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
2. Create a branch:
   ```bash
   git checkout -b add-org-libre-launcher
   ```
3. Copy `metadata/org.libre.launcher.yml` into `metadata/org.libre.launcher.yml` in your `fdroiddata` clone:
   ```bash
   cp metadata/org.libre.launcher.yml <fdroiddata-clone>/metadata/
   ```
4. Verify recipe locally with the `fdroid` build tools:
   ```bash
   fdroid checkupdates org.libre.launcher
   fdroid build org.libre.launcher:200
   ```
5. Commit and open a Merge Request at [GitLab fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/merge_requests).

### Recipe Contents (`metadata/org.libre.launcher.yml`):

```yaml
Categories:
  - Games
License: LGPL-3.0-or-later
AuthorName: Libre Launcher Contributors
SourceCode: https://github.com/PolskieWin/libre-launcher
IssueTracker: https://github.com/PolskieWin/libre-launcher/issues
Changelog: https://github.com/PolskieWin/libre-launcher/releases

AutoName: Libre Launcher

Summary: Fast Minecraft: Java Edition launcher with native Rust controls

Description: |-
  Libre Launcher is an open-source, lightweight, high-performance Minecraft:
  Java Edition launcher for Android.

  Features:
  * Native Rust touch controls and aim smoothing (libre_controls)
  * Thermal-aware AFK hibernate mode (5 FPS, pure-black OLED display)
  * Pre-boot mod compatibility scanner (Fabric, NeoForge, Quilt)
  * Custom Vulkan Turnip / Freedreno drivers and Mesa Zink renderer
  * 100% Material 3 design with home-screen pinned instance shortcuts
  * Zero telemetry, ads, or tracking SDKs

  DISCLAIMER: NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED
  WITH MOJANG OR MICROSOFT. Minecraft is a trademark of Mojang AB.

RepoType: git
Repo: https://github.com/PolskieWin/libre-launcher.git

AntiFeatures:
  - NonFreeNet

Builds:
  - versionName: '2.34'
    versionCode: 234
    commit: v2.34
    subdir: app_mojo
    submodules: true
    gradle:
      - assembleNoruntimeRelease

  - versionName: '2.35'
    versionCode: 235
    commit: v2.35
    subdir: app_mojo
    submodules: true
    gradle:
      - assembleNoruntimeRelease

  - versionName: '2.36'
    versionCode: 236
    commit: v2.36
    subdir: app_mojo
    submodules: true
    gradle:
      - assembleNoruntimeRelease

  - versionName: '2.37'
    versionCode: 237
    commit: v2.37
    subdir: app_mojo
    submodules: true
    gradle:
      - assembleNoruntimeRelease

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '2.37'
CurrentVersionCode: 237
```
