# BloxInsight / RBX Insight

A Kotlin Multiplatform + Compose Android APK inspection and patch-workbench starter.

> **Status:** source scaffold / prototype foundation. This is not a complete or production-ready APK patcher. The Apktool adapter, package installer, and privileged integrations are intentionally interfaces/stubs until their dependency versions, licenses, and platform behavior are validated.

## Goals
- Android API 24–36 compatibility target (requires real-device/emulator validation).
- Inspect APK metadata and hashes without modifying the source APK.
- Modular patch-engine contract for an Apktool-backed implementation.
- Explicit, user-confirmed Android package installation.
- Optional Shizuku, Dhizuku, KernelSU, Magisk, and APatch adapters behind capability interfaces.
- CI build/test workflow.
- Legacy Roblox package IDs recognized for inspection: `com.roblox.client` and `com.roblox.roblox_client`.

## Requirements
- JDK 17
- Android SDK and a compatible Android Studio installation
- Gradle wrapper generated/validated for the Gradle version selected by Android Studio

This scaffold intentionally does not include a Gradle wrapper JAR or binary because those should be generated from a verified Gradle distribution in your development environment.

## Project structure
- `composeApp`: Compose Multiplatform UI and shared domain models.
- `androidApp`: Android application entry point and platform adapters.
- `core`: patch API, inspection model, signing/verification contracts.
- `integrations`: privilege backend contracts and capability model.
- `.github/workflows/build.yml`: CI template.

## Build
Open in a recent Android Studio with Kotlin Multiplatform and Compose support, let Android Studio select compatible plugin versions, then run the Gradle tasks it generates. The workflow is a template and may need task-name adjustments after plugin versions are finalized.

For an Android-only first build, use the `androidApp` module's debug build task once the Gradle project is synced.

## Security notes
- Never patch or replace an installed app silently.
- Never bundle third-party APKs, signing keys, or proprietary Roblox binaries.
- Verify signatures and hashes; a hash alone does not prove an APK is trustworthy.
- Privileged operations are not implemented by this scaffold.
- Treat imported patch bundles as untrusted input.
- Review each upstream license before copying code or redistributing dependencies.

## Compatibility
The declared minimum API is 24. The target API and compile SDK should be aligned with the installed Android SDK and current Play/Android requirements. Native ABI and 16 KiB page-size support must be tested against every native dependency; this scaffold does not claim that validation has already passed.
