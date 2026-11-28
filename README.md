# NEXORA

NEXORA is a native Android app for running local GGUF language models directly on your device.

The goal is a straightforward, private, offline-first AI chat experience with character cards, customizable roleplay, persistent conversation history, and local model management. Model discovery and downloads may use the internet, but chat generation is intended to run on-device through llama.cpp.

## Project goals

- Native Android interface built with Kotlin and Jetpack Compose
- Local GGUF inference through llama.cpp
- Import and manage local models
- Character cards, scenarios, and editable prompts
- Persistent chats and continuity across model changes
- Transparent settings with no undisclosed prompt rewriting
- No paid APIs, subscriptions, accounts, or mandatory cloud inference

## Target device

Initial development and performance testing target: Google Pixel 9 Pro XL (arm64-v8a).

## Current implementation

- Android/Compose app shell
- Import GGUF files into app-private storage
- Basic GGUF magic-header validation and SHA-256 calculation
- Model library with select/delete controls
- Initial llama.cpp Android native runtime integration
- Basic local chat screen with optional visible/editable system prompt

The native runtime is an initial integration, not yet verified on a physical Pixel. Model compatibility, streaming, cancellation, memory use, and performance still require device testing. Chat persistence, character cards, and durable memory are planned but not yet implemented.

## Build

Clone with submodules so the pinned llama.cpp source is present:

```bash
git clone --recurse-submodules https://github.com/jaedwards1986/NEXORA.git
```

Open in Android Studio with JDK 17, Android SDK 35, NDK 29.0.13113456, and CMake 3.31.6. Or run:

```bash
gradle :app:assembleDebug
```

## Native runtime

NEXORA integrates the Android JNI binding from ggml-org/llama.cpp, pinned as a Git submodule. See docs/ARCHITECTURE.md.
