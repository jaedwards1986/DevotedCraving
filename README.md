# ForgeAI

ForgeAI is a public-source, local-first Android AI workspace targeting the Google Pixel 9 Pro XL.

## Updated architecture baseline

ForgeAI is designed to be self-contained and usable offline after its required model files have been downloaded. LLM inference and application data remain on the device; no external inference server or paid inference API is part of the target architecture.

### Target hardware

- Primary device: Google Pixel 9 Pro XL.
- System-on-chip: Google Tensor G4.
- Native ABI: ARM64 (arm64-v8a).

The Tensor G4 includes an on-device AI accelerator, but the presence of that accelerator does not automatically make an arbitrary GGUF model or llama.cpp build run on the NPU. NPU acceleration is a separate optimization project requiring a compatible runtime, model conversion/support, and device verification.

### Current dialogue engine

- Runtime: native llama.cpp.
- Model format: GGUF.
- Android integration: Kotlin with JNI.
- Inference: on-device; no remote model-serving endpoint.
- Existing ARM64 native build and GGUF model-management path are retained as the baseline.

The existing llama.cpp path must be proven with a real model load and generated response on the target device before changing runtimes or optimizing sampling. CPU/GPU execution details and performance must be measured on-device; successful native compilation alone does not prove runtime inference or NPU use.

### Future acceleration path

LiteRT (including its language-model tooling where appropriate) and Google's Tensor acceleration tooling are candidates for later investigation. They are not currently claimed as integrated or active. Any alternate backend must be optional, benchmarked against the working llama.cpp baseline, and must not change Natasha's identity, stored conversations, memories, or user interface.

### Planned on-device pipeline

1. Dialogue: GGUF model through llama.cpp/JNI.
2. Performance: measure load time, memory use, tokens per second, stability and thermal behavior; then optimize.
3. Character quality: evaluate prompt consistency and response quality without rewriting Natasha's locked character foundation.
4. Voice input: add Android SpeechRecognizer with an explicitly on-device recognition path where supported. Do not assume speech recognition is offline on every device/configuration; verify offline availability and provide a clear text-input fallback.
5. Image generation: add a separate, optimized on-device diffusion subsystem only after dialogue is stable. It will require its own model, runtime, storage and memory/performance testing.

The priority order is deliberate: **LLM stability → memory/speed → dialogue quality → speech → image generation.**

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
- JVM prompt tests cover Dirty Harry RP, Aion RP, Qwen3, Llama 3, generic templates, and attempts to override the core identity.
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

On first launch, ForgeAI automatically downloads, verifies and selects **L3.1-RP-Hero Dirty Harry 8B Q5_K_M** from the public Hugging Face repository `DavidAU/L3.1-RP-Hero-Dirty_Harry-8B-GGUF` (file `L3.1-RP-Hero-Dirty_Harry-8B-D_AU-q5_k_m.gguf`). Its author describes it as an uncensored NSFW roleplay model; the published Q5_K_M file is approximately 5.73 GB. It uses the standard Llama 3 chat template, which ForgeAI selects automatically. The model is not bundled in the APK; first launch requires an internet connection and sufficient free storage. The user does not have to search for, choose, or manually import the initial model. The Models tab remains available later to import, download, and switch models.

**Offline requirement:** after the GGUF and any required runtime assets are fully downloaded and verified, ordinary chat inference and access to locally stored conversations, memories and projects must work without a network connection. Hugging Face catalog discovery and downloading additional models require internet access. Model switching does not replace Natasha's app-level identity, personality pack, memories, conversations or project data.

The current 8B model remains the baseline until actual device benchmarks establish whether a smaller 3B–7B model would provide a better speed, memory and quality trade-off. Do not change the default model solely on theoretical NPU performance claims.

## Privacy and connectivity

The app uses HTTPS network access for public Hugging Face model discovery and downloads; inference and stored chat data remain local. The intended post-download operating mode is offline. Android cloud backup is disabled and cleartext traffic is rejected. Public gated repositories that require authentication are not downloadable through the current anonymous catalog flow. Automatic app binary updates are not implemented. Conversation and memory archives can be exported/imported manually; archives are currently unencrypted and should be stored securely. Automatic cloud sync and full-device restore are not implemented.

## Verification status and implementation gates

A successful CI build verifies compilation, native linking and APK packaging for the configured build targets. It does **not** by itself verify installation, GGUF loading, JNI inference, offline behavior, NPU acceleration, speech recognition or image generation on a physical device.

The next required device milestone is to:
1. Install the current APK on a Pixel 9 Pro XL and confirm launch.
2. Load the configured GGUF and record the exact model name, quantization, file size and load result.
3. Send a test prompt and verify that a response is generated through the local llama.cpp/JNI path.
4. Repeat a short inference test with network access disabled to confirm post-download offline operation.
5. Record load time, memory use, generation speed and thermal behavior before tuning or replacing the backend.

Do not mark a gate complete without direct device evidence. Preserve Natasha's character foundation and the existing interface during infrastructure verification.

## Build and verification

Open the repository in Android Studio with a compatible Android SDK and NDK. The GitHub Actions workflow installs the Android NDK and CMake toolchain, runs JVM unit tests, assembles debug and release variants, and publishes APK artifacts when the run succeeds. A green CI run, signed release APK, installation and Pixel 9 Pro XL device verification must be confirmed independently before calling a build release-ready.

## Important current limits

Project build plans write reviewed files inside the app-private project workspace; they do not yet execute Gradle or produce an APK on the phone. Model-generated plans depend on the selected local model following the structured proposal format, and every proposal still requires manual review and approval. Chat attachments currently accept bounded text/source files. Image generation, full voice interaction, authenticated gated-model downloads and a complete signed app updater are not implemented yet.
