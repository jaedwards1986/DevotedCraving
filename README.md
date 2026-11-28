# DavotedCraving

DavotedCraving is a local-first Android application for private companion chat, app-owned long-term memory, switchable local language models, and a source-linked Natasha Nice public archive.

## Current implementation

- Native Android / Kotlin / Jetpack Compose application.
- Dark black, crimson, and ember interface.
- First-launch PIN setup and PIN unlock. PIN verification uses PBKDF2-HMAC-SHA256 with a per-install random salt.
- Android secure-window flag to prevent ordinary screenshots and recent-app previews.
- On-device chat-history storage and a manually managed memory notebook.
- Dedicated navigation sections for companion chat, memory, public archive, local models, and settings.
- No remote chat inference or paid API integration.

## Not implemented yet

The chat screen currently stores messages but does not generate replies. llama.cpp / GGUF inference, model download and integrity verification, semantic memory retrieval, archive source connectors, authorized media download, voice, spatial audio, and maintenance scheduling remain future implementation work. Model candidates shown in the app are not claimed to be installed or Pixel-validated.

## Product decisions

- App display name: **DavotedCraving**
- GitHub repository: **NEXORA**
- Target device: Google Pixel 9 Pro XL
- Default model candidate: Llama 3 8B Instruct – Uncensored, Q4_K_S
- Alternative quantization: Q5_K_M
- Other models are testing candidates, not final selections.
- Persistent persona and memory belong to the application, not the model.
- The Natasha Nice archive is for authentic, source-linked public material and authorized media. It does not represent the real person as participating in private conversations.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.


## Build status
Local GGUF inference integration is being wired through the on-device llama.cpp runtime.
