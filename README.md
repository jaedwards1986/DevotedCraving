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

Initial development and performance testing target: Google Pixel 9 Pro XL.

## Current status

This repository has been reset from its AI Horde prototype to a clean Android foundation. The current app is a starter shell; local inference, model import, persistence, and character features are not yet implemented or validated.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35, or run:

```bash
gradle :app:assembleDebug
```

## Architecture direction

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
