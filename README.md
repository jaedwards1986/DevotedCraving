# ForgeAI

ForgeAI is a public-source, local-first Android AI workspace targeting the Google Pixel 9 Pro XL.

## Current implementation

- Kotlin, Jetpack Compose, Material 3 and Room persistence.
- Native llama.cpp JNI inference for local GGUF models (ARM64).
- GGUF import into app-private storage, SHA-256 recording and model switching.
- Token streaming and cancellation; native model load, inference and unload are serialized.
- Chat history with multiple conversations and bounded text/source-code attachments.
- Prompt-free natural conversation: Natasha's versioned personality is injected automatically, so users can speak normally without slash commands, magic phrases or repeated setup prompts.
- Natasha personality pack Version 71 preserves her permanently bold, sexually forward adult character and adapts automatically to each selected model's actual capabilities and rules.
- Natasha's adult fictional sex-worker character profile is stored separately from the selected LLM.
- An immutable character foundation is injected into every prompt, independent of conversation history and selected model; profile customizations cannot replace her established identity.
- The locked character voice is explicitly hypersexual, persistently horny, sexually forward, brazen, filthy-mouthed, dirty-minded, and identifies her as a stripper, prostitute, and sex-line operator, while preserving useful task-focused answers.
- JVM prompt tests cover Aion RP, Qwen3, Llama 3, generic templates, and attempts to override the core identity.
- Persistent memory list with search, editing, deletion, pinning, conservative automatic capture, and relevance-ranked recall.
- Full conversation and memory JSON archive export/import; imported conversation IDs are remapped to avoid merging with existing chats.
- Project workspace with project descriptions, file creation, editing, safe file moves/renames, and confirmed deletion.
- Project-aware prompts that include a bounded snapshot of selected source files while excluding hidden/generated folders and redacting common credentials and tokens.
- Natasha can propose multi-file project changes in a structured plan; ForgeAI parses the proposal, validates relative paths and size limits, and requires a human preview and explicit approval before writing.
- Reviewed project build plans with path validation, file-size limits and rollback on failed application.
- Project ZIP export/import with traversal checks, bounded archive sizes, staged restore, rollback on failure, and binary-safe file copying.
- Persistent project activity journal for file edits, build plans, archive imports/exports, with bounded journal growth.
- Model manager with GGUF import, public Hugging Face catalog search, in-app GGUF downloads, free-space reserve, size checks, SHA-256 verification when the hub supplies an LFS digest, safe removal and recoverable model switching.
- Daily WorkManager maintenance scheduling, database integrity checks, low-storage reporting, safe cleanup of abandoned temporary imports, and a manual maintenance trigger.
- Classy neutral dark ForgeAI interface; Natasha's adult personality stays in the chat rather than the visual branding.
- Android CI workflow configured for unit tests, debug/release APK assembly, and debug APK artifact upload.

## Model setup

On first launch, ForgeAI automatically downloads, verifies and selects **Aion RP Llama 3.1 8B Q5_K_M** from the public Hugging Face repository `bartowski/Aion-RP-Llama-3.1-8B-GGUF`. This 8B model is explicitly trained for uncensored roleplay and creative writing; its upstream model card specifies the native `__SYSTEM__`, `__USER__`, and `__ASSISTANT__` prompt format, which ForgeAI uses. The model is not bundled in the APK because it is approximately 5.73 GB; first launch requires an internet connection and sufficient free storage. The user does not have to search for, choose, or manually import the initial model. The Models tab remains available later to import, download, and switch models. Switching models does not replace Natasha's app-level identity, personality pack, memories, conversations or project data.

## Privacy and connectivity

The app uses HTTPS network access only for public Hugging Face model discovery and downloads; inference and stored chat data remain local. Android cloud backup is disabled and cleartext traffic is rejected. Public gated repositories that require authentication are not downloadable through the current anonymous catalog flow. Automatic app binary updates are not implemented. Conversation and memory archives can be exported/imported manually; archives are currently unencrypted and should be stored securely. Automatic cloud sync and full-device restore are not implemented.

## Build and verification

Open the repository in Android Studio with a compatible Android SDK and NDK. The GitHub Actions workflow installs the Android NDK and CMake toolchain, runs JVM unit tests, assembles debug and release variants, and publishes the debug APK as a short-lived workflow artifact when the run succeeds. A green CI run, signed release APK, installation and Pixel 9 Pro XL device verification must be confirmed independently before calling a build release-ready.

## Important current limits

Project build plans write reviewed files inside the app-private project workspace; they do not yet execute Gradle or produce an APK on the phone. Model-generated plans depend on the selected local model following the structured proposal format, and every proposal still requires manual review and approval. Chat attachments currently accept bounded text/source files. Image generation, full voice interaction, authenticated gated-model downloads and a complete signed app updater are not implemented yet.
