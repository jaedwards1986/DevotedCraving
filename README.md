# ForgeAI

ForgeAI is a private, local-first Android AI workspace targeting the Google Pixel 9 Pro XL.

## Current implementation

- Kotlin, Jetpack Compose, Material 3 and Room persistence.
- Native llama.cpp JNI inference for local GGUF models (ARM64).
- GGUF import into app-private storage, SHA-256 recording and model switching.
- Token streaming and cancellation; native model load, inference and unload are serialized.
- Chat history with multiple conversations and bounded text/source-code attachments.
- Natasha's adult fictional sex-worker character profile is stored separately from the selected LLM.
- An immutable character foundation is injected into every prompt, independent of conversation history and selected model; profile customizations cannot replace her established identity.
- The locked character voice is explicitly hypersexual, horny, provocative, shamelessly flirtatious, and identifies her as a stripper, prostitute, and sex-line operator, while preserving useful task-focused answers.
- JVM prompt tests cover Qwen3, Llama 3, generic templates, and attempts to override the core identity.
- Persistent memory list with search, editing and deletion.
- Project workspace with project descriptions, file creation, editing, safe file moves/renames, and confirmed deletion.
- Project-aware prompts that include a bounded snapshot of selected source files while excluding hidden/generated folders and redacting common credentials and tokens.
- Natasha can propose multi-file project changes in a structured plan; ForgeAI parses the proposal, validates relative paths and size limits, and requires a human preview and explicit approval before writing.
- Reviewed project build plans with path validation, file-size limits and rollback on failed application.
- Project ZIP export/import with traversal checks, bounded archive sizes, staged restore, rollback on failure, and binary-safe file copying.
- Persistent project activity journal for file edits, build plans, archive imports/exports, with bounded journal growth.
- Model manager with GGUF import, free-space reserve, SHA-256 verification, safe removal and recoverable model switching.
- Daily WorkManager maintenance scheduling, database integrity checks, low-storage reporting, safe cleanup of abandoned temporary imports, and a manual maintenance trigger.
- Classy neutral dark ForgeAI interface; Natasha's adult personality stays in the chat rather than the visual branding.
- Android CI workflow configured for unit tests, debug/release APK assembly, and debug APK artifact upload.

## Model setup

No model is bundled. Import a compatible GGUF file from the Models tab. Qwen3 8B and Llama 3.1 8B GGUF builds are intended model families; check available RAM, quantization and context requirements before importing. A model switch does not replace Natasha's app-level profile or stored project data.

## Privacy and connectivity

The app currently has no network permission, disables Android cloud backup and rejects cleartext traffic. Model inference and app data are local. Online model discovery, downloads and automatic app updates are not implemented yet. Full-device backup/restore is still being expanded.

## Build and verification

Open the repository in Android Studio with a compatible Android SDK and NDK. The GitHub Actions workflow installs the Android NDK and CMake toolchain, runs JVM unit tests, assembles debug and release variants, and publishes the debug APK as a short-lived workflow artifact when the run succeeds. A green CI run, signed release APK, installation and Pixel 9 Pro XL device verification must be confirmed independently before calling a build release-ready.

## Important current limits

Project build plans write reviewed files inside the app-private project workspace; they do not yet execute Gradle or produce an APK on the phone. Model-generated plans depend on the selected local model following the structured proposal format, and every proposal still requires manual review and approval. Chat attachments currently accept bounded text/source files. Image generation, full voice interaction, online model discovery/downloads, and a complete signed updater are not implemented yet.
