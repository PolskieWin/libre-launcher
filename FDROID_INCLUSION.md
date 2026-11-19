# F-Droid Official Inclusion Guide

This document details the compliance architecture, anti-features disclosure, build reproducibility, and exact merge request procedures for publishing **Libre Launcher** to the official **F-Droid Main Repository** (`fdroiddata`).

---

## 1. Compliance Audit & Summary of Changes

To comply strictly with official F-Droid inclusion policies and automated builder constraints, the following requirements are fulfilled:

| Requirement | Status | Implementation Details |
|---|---|---|
| **Free / Libre Open Source License** | Compliant | Licensed under **GNU LGPL v3.0** (`LICENSE`). Fully compatible with F-Droid inclusion guidelines. |
| **No Proprietary Tracking / Ads** | Compliant | Zero analytics, zero Firebase, zero Crashlytics, zero advertising SDKs (verified 0 trackers via Exodus Privacy). |
| **No Prebuilt Binary Blobs in Maven** | Compliant | Removed `gson-2.8.6.jar`, `ExagearApacheCommons.jar`, and `exp4j-0.4.9-SNAPSHOT.jar` from `app_mojo/libs/`. Replaced with official Maven Central dependencies (`gson:2.10.1`, `commons-io:2.15.1`, `commons-compress:1.26.0`, `commons-lang3:3.14.0`, `exp4j:0.4.8`). |
| **No Copyright-Infringing Assets** | Compliant | Removed unreferenced proprietary `minecraft_ten.ttf` font containing Mojang AB copyright headers from resources. |
| **Trademark Disclaimers** | Compliant | Added mandatory Mojang/Microsoft trademark disclaimer across `README.md`, `strings.xml`, `wiki_dialog_content`, and Fastlane metadata. |
| **Fastlane Metadata Structure** | Compliant | Standard Fastlane structure generated at `fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`, and 512x512 `icon.png`. |
| **Deterministic Versioning** | Compliant | Updated `app_mojo/build.gradle` to support `-PversionCode` and `-PversionName` properties, environment variables, git commit counts, and semantic tags instead of hardcoded fallback numbers. |
| **Reproducible No-Runtime Flavor** | Compliant | `noruntime` flavor (`assembleNoruntimeRelease`) does not trigger arbitrary zip downloads during Gradle build execution. |
| **Source-Built Native Libraries** | Compliant | Native components (`libfreetype.so`, `libGLMojo.so`, `libremobile`, `libre_controls`) compile directly from source submodules without precompiled blobs. |

---

## 2. Anti-Features Disclosure

Because Libre Launcher interacts with Microsoft authentication services and downloads Minecraft: Java Edition game components, F-Droid requires declaring the following **Anti-Features**:

1. **`NonFreeNet`** (Non-Free Network Services):
   - **Reason**: The application connects to Microsoft / Mojang authentication endpoints (`login.live.com`, `api.minecraftservices.com`) and downloads official game assets from Mojang/Microsoft content delivery networks.
   - **F-Droid Policy**: Allowed, provided the application itself is 100% open-source and the anti-feature is explicitly flagged in the metadata.
2. **`NonFreeDep`** (Non-Free Dependencies):
   - **Reason**: Running Minecraft: Java Edition requires official game files and a legitimate Microsoft/Mojang account.

---

## 3. Official F-Droid Main Repository Submission (`fdroiddata`)

To submit directly to the official F-Droid build server repository:

1. **Push release tag** to GitHub and GitLab mirrors:
   ```bash
   git tag -a v2.60 -m "Release v2.60"
   git push origin v2.60
   git push gitlab v2.60
   ```

2. **Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata)** on GitLab.

3. **Create a feature branch**:
   ```bash
   git checkout -b add-org-libre-launcher
   ```

4. **Copy the recipe** into your `fdroiddata` clone:
   ```bash
   cp metadata/org.libre.launcher.yml <fdroiddata-clone>/metadata/
   ```

5. **Verify the recipe locally** using the `fdroid` build tools:
   ```bash
   fdroid checkupdates org.libre.launcher
   fdroid lint org.libre.launcher
   fdroid build --stop --test org.libre.launcher:260
   ```

6. **Submit a Merge Request**:
   - Push your branch to your GitLab fork: `git push origin add-org-libre-launcher`
   - Open a Merge Request at [GitLab fdroid/fdroiddata Merge Requests](https://gitlab.com/fdroid/fdroiddata/-/merge_requests).
   - Reference the official F-Droid inclusion MR (`!58168`).

---

## 4. Recipe Contents (`metadata/org.libre.launcher.yml`)

```yaml
Categories:
  - Games
License: LGPL-3.0-or-later
AuthorName: Libre Launcher Contributors
SourceCode: https://gitlab.com/libre-group2305847/Libre-project
IssueTracker: https://gitlab.com/libre-group2305847/Libre-project/-/issues
Changelog: https://gitlab.com/libre-group2305847/Libre-project/-/releases

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
Repo: https://gitlab.com/libre-group2305847/Libre-project.git

AntiFeatures:
  - NonFreeNet
  - NonFreeDep

Builds:
  - versionName: '2.60'
    versionCode: 260
    commit: v2.60
    subdir: app_mojo
    submodules: true
    gradle:
      - assembleNoruntimeRelease

AutoUpdateMode: Version
UpdateCheckMode: Tags
UpdateCheckData: app_mojo/build.gradle|versionCode\s+(\d+)|app_mojo/build.gradle|versionName\s+["']([^"']+)["']
CurrentVersion: '2.60'
CurrentVersionCode: 260
```
