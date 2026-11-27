# DavotedCraving

Android application foundation for the DavotedCraving product.

## Product standard

DavotedCraving is one unified Natasha Nice-centered application combining its interactive AI experience, adult fantasy and roleplay systems, persistent app-owned memory, immersive sensory features, and authentic source-linked archive.

## Current implementation status

This repository starts from a clean Android foundation. The current commit provides a Gradle/Compose application shell and CI build workflow. It does **not** yet claim to provide local inference, persistent memory, a PIN vault, voice, sensory orchestration, or archive ingestion.

## Non-negotiable engineering constraints

- Target device: Google Pixel 9 Pro XL.
- AI inference must run locally on-device. No paid APIs or remote chat inference.
- Model-independent application state must survive model changes and app updates.
- No silent model substitutions. The previously accepted default candidate remains **Llama 3 8B Instruct – Uncensored, Q4_K_S**; it is not bundled or integrated in this foundation.
- No subscriptions, paid APIs, memberships, in-app purchases, credits, paywalls, or hidden fees.
- Archive assets must be authentic and source-linked, with provenance recorded.

## Build

Requirements: JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0.

```bash
gradle --version
gradle :app:assembleDebug
```

The GitHub Actions workflow installs the Android SDK and runs the same debug APK build.

## Package

- Display name: DavotedCraving
- Application ID / namespace: `ai.davoted.craving`
- Minimum SDK: 34
- Compile / target SDK: 35
