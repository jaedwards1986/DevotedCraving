# Phase Execution Plan: Model Core, Context State, and RAG

**Execution order is mandatory:** Bridge -> Model -> Parameters -> History -> RAG.

This plan supersedes any interpretation that a JNI connectivity probe or a bundled CI model fixture means local inference is operational.

Deployment memory, threading, asynchronous lifecycle, retry, rollback, and sampler-boundary tests are specified in [Deployment Readiness Triage](DEPLOYMENT_READINESS_TRIAGE.md). Pixel-specific backend selection is gated by [Pixel 9 Pro XL Accelerator Feasibility](PIXEL_ACCELERATOR_FEASIBILITY.md): CPU first, Vulkan as an optional measured experiment, and no NNAPI/NPU claim without a maintained backend and device proof. Those tests are part of the Phase 1/2 model-hardening gate; RAG remains blocked until the physical-device model gate passes.

## Baseline audit

At the time this plan was written:

- The Android app has an arm64-v8a JNI library, but the native code is a bridge diagnostic, not llama.cpp.
- The Kotlin NativeBridge checks JNI loading, UTF-8 round-tripping, and integer-array marshalling. It has no model load, unload, tokenize, decode, or generation methods.
- The chat repository still sends history to an OpenAI-compatible HTTP streaming client. Its payload is currently capped with `takeLast(40)`, which is a row/message limit, not a turn-aware 20-exchange limit.
- Temperature is persisted in app settings, but top-p, frequency penalty, and presence penalty are not wired to a local sampler.
- The current CI model fixture is Qwen3 4B Instruct Q4_K_M. It is not the selected production model and the app does not load it.
- No RAG/key-fact ingestion pipeline is implemented.

A successful CMake build or JNI health check is necessary but insufficient evidence of local inference.

## Phase 1 — Model integration and performance tuning (Power Core)

**Goal:** Load and generate with the selected Meta Llama 3 8B Instruct Q5_K_M GGUF through the embedded Android runtime, then measure it and pass sampler settings.

### 1.1 Pin and provision the production model

Selected target:

- Family: Meta Llama 3 8B Instruct
- Format: GGUF
- Quantization: Q5_K_M
- Candidate artifact: `bartowski/Meta-Llama-3-8B-Instruct-GGUF/Meta-Llama-3-8B-Instruct-Q5_K_M.gguf`
- Published size: approximately 5.73 GB
- Upstream base model: `meta-llama/Meta-Llama-3-8B-Instruct`

The candidate quantization page identifies the original model and quantization recipe, but a mutable `main` URL is not a reproducible artifact pin. Before enabling in-app download, record the immutable repository revision, exact filename, exact byte size, SHA-256, upstream model identity, quantizer provenance, and applicable license/terms. The user must obtain any gated access and accept applicable terms themselves. Do not check model weights into Git or package a multi-gigabyte model in every APK.

The model manager must support an app-private model directory, resumable download/import, temporary-file staging, checksum verification, atomic promotion after verification, and cleanup of abandoned partial downloads. A corrupt or incomplete file must never be offered to the runtime.

### 1.2 llama.cpp runtime integration

- Pin an exact llama.cpp commit (not a moving branch) and record its license and build options.
- Build only `arm64-v8a` initially using the Android NDK/CMake toolchain.
- Link llama.cpp/ggml into the existing native target; do not substitute a local HTTP server or an external inference service.
- Expose a narrow JNI API for load, unload, generate/stream, cancel, runtime information, effective sampler configuration, and structured errors.
- Keep native model/context handles opaque to Kotlin. Serialize operations on a single model context unless the selected runtime explicitly supports safe concurrency.
- Never load/unload or generate on the Android main thread.
- Use the model's Llama 3 Instruct chat template, including correct special-token boundaries and end-of-turn handling.
- Keep the existing remote client isolated until local inference has passed the device acceptance tests; do not silently route to localhost.

### 1.3 GGUFManager lifecycle abstraction

Implement a Kotlin `GGUFManager` above the native engine with an explicit state machine:

- `NotLoaded`
- `Validating`
- `Loading`
- `Ready(modelId, runtimeInfo)`
- `Generating`
- `Unloading`
- `Error(code, message)`

Responsibilities:

- Asynchronous load/unload on an IO/native dispatcher.
- One active model context at a time for the initial milestone.
- Model metadata/catalog lookup and file-integrity validation before load.
- Idempotent load of the already active model.
- Cancellation and orderly unload.
- Hot-swap by validating the replacement first, then stopping generation and replacing the active context in a controlled sequence. Do not keep two 5.73 GB model mappings resident simultaneously on the phone.
- Retain the previous model file and selected-model metadata so a failed replacement can restore the prior model.
- Emit observable state for the UI and diagnostics.
- Never label a model Ready until native initialization and a small generation probe both succeed.

The first implementation may use a single-model cache. Do not add an in-memory multi-model cache on a 16 GB phone without measured memory headroom.

### 1.4 Native performance metrics

Add structured native metrics around model load and each generation:

- model file size and model identity (no user prompt content);
- model-load duration;
- prompt token count and prompt-processing duration;
- generated token count and generation duration;
- tokens/second, computed as generated tokens divided by generation duration;
- time to first token;
- peak resident memory / process PSS where available, clearly labeling the measurement source and whether it is process-wide or model-specific;
- context size, batch settings, thread count, backend, ABI, runtime commit;
- cancellation, allocation failure, and out-of-memory events.

Use monotonic clocks. Do not infer model-only RAM from process PSS. Capture a baseline before load, after load, and during/after generation. Avoid logging prompt text, API keys, or private conversation content.

### 1.5 Initial sampler injection

The required initial values are:

| Parameter | Initial value |
|---|---:|
| Temperature (T) | 0.95 |
| Top-P (P) | 0.95 |
| Frequency penalty (FP) | 0.5 |
| Presence penalty (PP) | 0.3 |

Create a typed Kotlin sampler configuration and map it to the exact sampler API supported by the pinned llama.cpp revision. Pass the effective values through JNI; return/log the effective native values for verification.

Do not conflate frequency/presence penalties with repetition penalty. If the pinned llama.cpp revision lacks an exact equivalent, document the limitation and implement an explicitly tested compatible sampler rather than silently dropping the parameter.

### Phase 1 acceptance gate — do not proceed to RAG

All must pass on a physical arm64 Android device:

1. CI builds and packages the pinned llama.cpp native runtime.
2. The exact selected GGUF passes SHA-256 validation in app-private storage.
3. GGUFManager loads the model asynchronously and reports Ready.
4. A fixed Llama 3 Instruct prompt produces non-empty streamed tokens through NativeBridge with network disabled.
5. Cancellation, unload, invalid file, corrupted file, and failed-load paths return controlled errors.
6. Repeated load/unload does not leak contexts or accumulate resident model memory.
7. T/P/FP/PP values are confirmed at the native sampler boundary.
8. Benchmark output includes load time, time-to-first-token, tokens/sec, and memory readings.
9. A sustained generation run completes without crash or thermal/memory failure on the target device.

A workstation-only benchmark or JNI health check does not satisfy this gate.

## Phase 2 — State complexity (Memory and Context Manager)

**Start only after Phase 1 passes.** RAG ingestion is explicitly blocked until reliable local Llama 3 generation through NativeBridge has been demonstrated.

### 2.1 Turn-aware history truncation

Replace the current message-row cap with a turn-aware 20-exchange window:

- Define an exchange as one user message and its corresponding assistant response.
- Load messages in chronological order.
- Exclude error placeholders and handle interrupted/partial assistant responses consistently.
- Keep the latest 20 complete exchanges, plus a clearly handled current user turn.
- Preserve the system prompt outside the exchange counter.
- Apply a token-budget check after formatting the Llama 3 chat template. A 20-exchange limit is a maximum, not permission to exceed the model context window.
- Reserve context capacity for system prompt, current input, and requested output.
- Do not delete historical Room rows merely to truncate the prompt. Truncation should affect the inference payload; conversation history remains available unless the user explicitly deletes it.

Acceptance: repository tests cover 0, 1, 20, 21+ exchanges, missing assistant replies, error rows, chronological order, and token-budget fallback.

### 2.2 Offline Key Facts ingestion skeleton

After the history gate passes, add a lightweight local ingestion worker:

- Scheduled with WorkManager and also callable manually.
- Runs only on-device and offline; no hosted LLM, API key, embeddings endpoint, or external service.
- Reads eligible Room history incrementally using a stable last-processed message ID/timestamp cursor.
- Extracts candidate key facts using a deterministic, bounded local extraction approach initially. Do not pretend a separate LLM extraction model exists unless one is actually installed and invoked.
- Store the resulting Key Facts artifact separately from the raw Room transcript.
- Include source conversation/message references, creation/update timestamps, confidence/provenance, and a schema version.
- Deduplicate and update facts deterministically; provide deletion propagation when a source conversation is deleted.
- Keep the ingestion process idempotent, cancellable, bounded in CPU/memory, and safe across app restarts.
- Do not inject facts into prompts in this phase. Retrieval and prompt injection are Phase 3.

Acceptance: offline worker test, incremental cursor test, duplicate/idempotence test, deletion propagation test, and restart recovery test.

## Phase 3 — Retrieval and prompt integration (deferred)

Only after Phase 2's Key Facts artifact is reliable:

- Implement retrieval over the separately stored facts.
- Rank/filter facts with provenance and confidence thresholds.
- Inject a bounded, clearly delimited memory section into the Llama 3 prompt.
- Test stale/conflicting facts, corrections, deletions, uncertainty, and no-match cases.
- Natasha must not present low-confidence or absent facts as remembered truth.

## Non-negotiable execution rules

- Preserve the selected Llama 3 8B Q5_K_M production target; Qwen3 CI fixture is not a replacement.
- Do not start RAG before model load and generation pass on-device.
- Do not claim offline support until generation succeeds with network disabled.
- Do not claim NPU acceleration without a working backend and device profiling.
- Do not report throughput or memory values until measured on the target device.
- Keep each stage independently testable and update the feature matrix only when evidence exists.
