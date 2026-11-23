# ForgeAI

ForgeAI is a public-source, edge-AI Android workspace targeting the Google Pixel 9 Pro XL. Its target architecture performs all AI inference and generation on the phone, without external inference servers or paid cloud AI services.

## Core strategy: Edge AI & model optimization

**Non-negotiable product requirement:** after model/runtime assets have been downloaded, all LLM inference and diffusion image generation must execute on the device's dedicated AI acceleration hardware (Tensor/TPU-class accelerator or an equivalent supported mobile accelerator). Cloud inference is not part of the product architecture.

The target device is the **Google Pixel 9 Pro XL with Google Tensor G4** (not Tensor G3). The accelerator requirement is a target and acceptance criterion, not a claim that the current llama.cpp build already uses the Tensor NPU. A model/runtime combination is not considered accelerator-enabled until device telemetry or a supported runtime's diagnostics demonstrate that execution is actually placed on the accelerator. If a model cannot be accelerated on the target hardware, select or convert a compatible model/runtime rather than silently treating CPU execution as completion of this requirement.

### 1. Model selection

#### Dialogue and narrative LLM

Prioritize compact, quantized models in the **3B–7B parameter range**, with mobile-friendly INT4/INT8 or equivalent quantization and an accelerator-supported execution path. Candidate model families include compact Llama variants and Phi-3-class models; TinyLlama is a smaller fallback candidate, not the preferred quality target. Evaluate model licensing, tokenizer/chat-template compatibility, memory footprint, latency, output quality and actual accelerator support before selecting a default.

The dialogue model should be evaluated for Natasha's adult fictional roleplay and general conversational/task performance. Fine-tuning or adapters may be considered where lawful and technically supported; do not assume that a model advertised as fine-tuned for a particular style will preserve the app's character behavior without prompt and regression testing.

#### Diffusion image generator

Do not target full SDXL as the default real-time mobile generator. Prioritize mobile-sized diffusion architectures and optimized variants such as MobileDiffusion, Stable Diffusion Turbo-class models, or other lightweight distilled/pruned models with a verified mobile accelerator backend. Favor generation speed, memory efficiency and stable thermal behavior over maximum photorealism. The diffusion model is a separate model/runtime from the dialogue LLM.

### 2. Runtime and implementation framework

- **Primary mobile acceleration direction:** LiteRT (the current evolution of TensorFlow Lite) and compatible accelerator delegates/backends where supported by the Pixel device.
- **Hardware acceleration:** investigate Google's supported Tensor/TPU acceleration interfaces and the exact runtime/model combinations they support. Do not infer NPU use merely because the phone contains a Tensor accelerator.
- **Current repository baseline:** native llama.cpp with GGUF models through Kotlin/JNI is already present and is retained for the existing implementation and comparison tests. It is not, by itself, proof of Tensor NPU execution. It must be replaced, adapted, or paired with a verified accelerator path to satisfy the final all-AI-compute-on-accelerator requirement.
- PyTorch Mobile is not the preferred new implementation path; favor currently supported mobile runtimes and documented accelerator integrations.
- The native Kotlin/Android application orchestrates input, model execution, image generation and presentation. Keep model execution local and modular so backends can be tested independently.

### 3. On-device workflow

1. **Voice input — Android SpeechRecognizer:** capture speech and convert it to text. Request/check an on-device recognition path where the OS and installed language assets support it. Verify offline operation on the target device; retain text entry as a fallback if offline speech recognition is unavailable.
2. **LLM director — compact quantized model:** send the recognized or typed text to the local dialogue model using the selected accelerator-supported runtime. The model produces Natasha's narrative response and, when an image is requested, a structured descriptive prompt for the image subsystem. All model inference must remain on-device and use the supported AI accelerator.
3. **Diffusion artist — optimized lite model:** pass the structured image prompt to a separate, optimized diffusion model. Execute diffusion on the device's supported GPU/NPU/AI accelerator path; measure generation latency, memory and thermals. No remote image-generation service.
4. **Output — native ForgeAI UI:** display the narrative response and generated image in the app. Preserve conversation history and existing app data locally.

### 4. Extreme mobile setup

| Layer | Component | Optimization / acceptance target | Location |
|---|---|---|---|
| Input | Android SpeechRecognizer | On-device speech recognition; offline verified where supported | Pixel OS |
| Logic | Quantized 3B–7B LLM | INT4/INT8; LiteRT or another verified accelerator runtime | On-device AI accelerator |
| Generator | MobileDiffusion / Turbo / Lite diffusion | Distilled, pruned or quantized; accelerator-backed | On-device AI accelerator |
| Orchestration | Kotlin Android app | Manages pipeline, model lifecycle and state | App |
| Output | Native Compose UI | Displays text and generated images | App |

The Pixel 9 Pro XL's Tensor G4 is the target hardware. The exact accelerator APIs, supported operators, model formats and runtime delegates must be verified against current Google documentation and on-device tests. A CPU/GPU fallback may be useful for development diagnostics, but it does **not** satisfy the final dedicated-AI-accelerator acceptance requirement for LLM or diffusion compute.

### 5. Implementation order

1. Verify the existing local GGUF load and llama.cpp/JNI generation path as a functional baseline.
2. Inventory Pixel Tensor accelerator support and benchmark compatible LiteRT/Tensor runtime options.
3. Select a compact 3B–7B dialogue model with a verified accelerator path; compare quality, latency, memory and thermals.
4. Integrate and verify accelerator execution for dialogue. Keep Natasha's existing character foundation unchanged.
5. Add offline-capable speech input and verify behavior with airplane mode enabled.
6. Integrate a separate lightweight diffusion model and verify that image generation executes on the supported device accelerator.
7. Run full end-to-end offline tests: speech/text → LLM → optional diffusion → native UI.

The priority is **accelerator feasibility → dialogue stability → memory/speed → character quality → speech → image generation**. Do not start diffusion integration until the dialogue accelerator path is stable.

## Current implementation

- Kotlin, Jetpack Compose, Material 3 and Room persistence.
- Native llama.cpp JNI inference for local GGUF models (ARM64); accelerator/NPU execution is not yet verified.
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

## Model setup and migration

The currently configured first-launch model is **L3.1-RP-Hero Dirty Harry 8B Q5_K_M** from the public Hugging Face repository `DavidAU/L3.1-RP-Hero-Dirty_Harry-8B-GGUF` (file `L3.1-RP-Hero-Dirty_Harry-8B-D_AU-q5_k_m.gguf`), approximately 5.73 GB. This is the existing functional baseline, **not the final optimized model target**: it is larger than the new preferred 3B–7B range, and its current llama.cpp path is not evidence of Tensor accelerator use.

Model migration must be evidence-led. Evaluate candidate 3B–7B models for quality, quantization, context requirements, chat-template support, storage/RAM needs, license and compatibility with an accelerator-supported runtime. Do not silently replace the current model until the replacement is validated and a safe model-switch/rollback path is confirmed. The user should retain the ability to download models while all subsequent inference remains offline.

**Offline requirement:** once models, tokenizers and required runtime assets have been downloaded and verified, dialogue, image generation, and access to locally stored conversations, memories and projects must operate without a network connection. Model catalog discovery and additional downloads may require internet access. No remote inference or image-generation endpoint is permitted.

## Privacy and connectivity

Network access is limited to optional public model discovery and model/runtime downloads. Inference and generated images must remain local. Android cloud backup is disabled and cleartext traffic is rejected. Public gated repositories that require authentication are not downloadable through the current anonymous catalog flow. Automatic app binary updates are not implemented. Conversation and memory archives can be exported/imported manually; archives are currently unencrypted and should be stored securely. Automatic cloud sync and full-device restore are not implemented.

## Verification gates

A successful CI build verifies compilation, native linking and APK packaging for configured targets. It does **not** prove model loading, inference, offline behavior, accelerator use, speech recognition or image generation on a physical device.

Track these gates independently:

1. Install the APK on a Pixel 9 Pro XL and confirm launch.
2. Load the current GGUF and record exact model name, quantization, file size and load result.
3. Generate a response through the local llama.cpp/JNI baseline.
4. Benchmark candidate compact LLMs and verify actual Tensor accelerator execution using supported runtime diagnostics/device telemetry.
5. Confirm dialogue inference remains operational with network access disabled.
6. Verify on-device SpeechRecognizer behavior offline.
7. Generate an image with the selected lite diffusion model and verify accelerator execution and offline operation.
8. Measure latency, peak memory, battery impact and thermal behavior for both LLM and diffusion workloads.

Do not mark accelerator, offline, speech or image-generation gates complete without direct device evidence. Preserve Natasha's character foundation and the existing interface during engine work.

## Build and verification

Open the repository in Android Studio with a compatible Android SDK and NDK. The GitHub Actions workflow installs the Android NDK and CMake toolchain, runs JVM unit tests, assembles debug and release variants, and publishes APK artifacts when the run succeeds. A green CI run, signed release APK, installation and Pixel 9 Pro XL device verification must be confirmed independently before calling a build release-ready.

## Important current limits

Project build plans write reviewed files inside the app-private project workspace; they do not yet execute Gradle or produce an APK on the phone. Model-generated plans depend on the selected local model following the structured proposal format, and every proposal still requires manual review and approval. Chat attachments currently accept bounded text/source files. Image generation, full voice interaction, verified Tensor NPU inference, and a complete signed app updater are not implemented yet.
