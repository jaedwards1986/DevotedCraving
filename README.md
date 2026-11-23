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
- **Current checked-in source status:** the current `main` source tree does not contain a llama.cpp/JNI runtime, GGUF importer, or local model manager. The chat path currently uses an OpenAI-compatible HTTP streaming client. Earlier statements in this README describing an existing native runtime and GGUF model manager were inaccurate for the checked-in source and are superseded by this status. The first implementation task is to add and verify the embedded local runtime; do not point the current HTTP client at a made-up local endpoint.
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

1. Add an embedded Android local-inference runtime and a real GGUF model lifecycle (download/import, validation, load, unload, cancellation and error handling).
2. Integrate the selected **Meta Llama 3 8B Instruct, GGUF Q5_K_M** model as the primary compatibility target; keep the multi-gigabyte model file separate from the APK.
3. Verify CPU/GPU llama.cpp inference as a functional baseline, then inventory Pixel Tensor G4 accelerator support and benchmark compatible LiteRT/Tensor runtime options. GGUF through llama.cpp does not automatically use the Tensor NPU.
4. Compare model load time, first-token latency, tokens/second, peak memory, thermal behavior and conversation quality. Keep Natasha's existing character foundation unchanged.
5. Only claim dedicated accelerator execution after device-side diagnostics prove it; if the selected GGUF/runtime cannot use the NPU, treat that as an unresolved acceptance gap.
6. Add offline-capable speech input and verify behavior with airplane mode enabled.
7. Integrate a separate lightweight diffusion model and verify image generation on the supported device accelerator.
8. Run full end-to-end offline tests: speech/text → LLM → optional diffusion → native UI.

The priority is **accelerator feasibility → dialogue stability → memory/speed → character quality → speech → image generation**. Do not start diffusion integration until the dialogue accelerator path is stable.

## Current implementation (verified source-tree status)

- Kotlin, Jetpack Compose, Material 3 and Room persistence.
- DataStore-backed settings, including Natasha's default system prompt.
- OpenAI-compatible HTTP/SSE chat client, endpoint/model settings, model-list discovery and connection testing.
- Conversation history, streaming/cancellation UI, and prompt injection through the chat repository.
- The current default endpoint/model remain `https://api.openai.com/v1` and `gpt-4o-mini`; this is a remote API configuration, not a local Llama configuration.
- The checked-in source tree currently has no embedded llama.cpp/JNI runtime, GGUF model importer/downloader, local model manager, Tensor NPU backend, or diffusion subsystem.
- A successful build of this source would therefore not establish local LLM inference or offline operation.

This section intentionally reports only components visible in the current `main` source tree. Earlier descriptions of native llama.cpp inference, GGUF import/model switching, and diffusion are not supported by the currently inspected source tree and must not be treated as implemented.

## Model setup and migration

The selected primary model target is **Meta Llama 3 8B Instruct, GGUF Q5_K_M**. A published GGUF listing reports approximately **5.73 GB** for this quantization; the exact repository/file and checksum must be pinned before implementing downloads. Meta's official model card describes the instruction-tuned model as intended for dialogue and lists an 8K context window. Model access is governed by Meta's Llama 3 Community License and applicable usage policy; free access does not mean public-domain or license-free.

This is a **selected integration target, not an implemented model**. The current source still defaults to the remote OpenAI-compatible endpoint and has no embedded GGUF runtime. Do not change the default endpoint to localhost until an embedded runtime exists; changing only the model ID would not make Llama run locally. The implementation must add an on-device runtime, a real model lifecycle and the correct Llama 3 chat template, then test on the Pixel 9 Pro XL.

The user's chosen Q5_K_M target is retained even though it is outside the earlier 3B–7B mobile-optimization preference. Benchmark memory use, context/KV-cache size, thermal behavior and sustained token speed on the target device before treating it as a viable default. Keep the model downloadable separately from the APK.

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
