# Deployment Readiness Triage: Android Model Hardening

**Scope:** Llama 3 8B Instruct Q5_K_M on the Pixel 9 Pro XL (Tensor G4, 16 GB RAM).

**Current status:** This is a test and implementation specification, not evidence that llama.cpp or the selected model is integrated. The existing JNI library is still a bridge diagnostic. Do not start RAG while this document's Phase 1/2 gates remain open.

## Measurement model: understand what memory means

llama.cpp is intended to run in-process through JNI, not as a separate Android process. Therefore, do not describe its memory as a separate "llama.cpp process RSS".

Collect these measurements from the Android app process:

- RSS: resident pages mapped into the process.
- PSS: shared pages proportionally attributed to the process.
- Private dirty / private clean where available.
- Native heap and Java heap separately where Android tooling exposes them.
- GGUF file size and mmap/file-backed mapping size, reported separately from resident memory.
- Context/KV-cache allocation, compute buffers, and backend allocations where the runtime can report them.

Take a baseline before load, after load and before generation, during generation, after generation, and after unload. Record Android version, device build, runtime commit, model checksum, context length, batch size, and backend. Android Studio Profiler and `dumpsys meminfo` can provide complementary process-level readings. Do not add RSS, PSS, and heap values together; they overlap and describe different accounting views.

A 5.73 GB GGUF file size is not the same as 5.73 GB resident RAM. With memory mapping, some model pages may remain file-backed and become resident on demand. Conversely, KV cache, compute buffers, allocator overhead, and runtime structures add memory beyond the model file. The 16 GB device capacity is not all available to ForgeAI; Android and other apps need headroom.

## Phase 1 — Resource boundary stress test

### 1.1 Memory profile

Run at least these checkpoints:

1. Fresh app baseline.
2. Model file verified but not loaded.
3. Model loaded, before prompt evaluation.
4. Prompt evaluated, before first generated token.
5. Sustained generation at the target context.
6. Generation stopped and context retained.
7. Model unloaded and cleanup completed.

Repeat at more than one context length (for example, a short context and the intended production context). Record peak and steady-state values. Check Android low-memory events and whether the OS kills the process. Repeat after a cold start to avoid relying on warm filesystem cache.

Acceptance:
- No app crash, ANR, or OS low-memory kill in the defined test.
- Memory values are timestamped and clearly labeled as process-wide or runtime-reported.
- Model-mapped bytes are not misrepresented as resident RAM.
- After unload, runtime-owned allocations return close to the pre-load baseline; document unavoidable allocator/page-cache retention.
- Establish a safe context-size ceiling based on observed memory headroom, not guesswork.

### 1.2 Thread-pool saturation and tuning

First query the device's available processors at runtime. Benchmark a controlled set such as 1, 2, 4, the reported physical-core count, and one oversubscribed value (physical cores + 1). Record the actual llama.cpp thread settings and distinguish prompt-processing threads from generation threads if the pinned revision exposes both.

For each setting, use the same model, prompt, context, sampler, seed, token budget, and thermal starting condition. Measure prompt-processing speed, time to first token, generation tokens/sec, peak memory, CPU utilization, and temperature/thermal status. Run multiple repetitions and report median and range.

Do not assume more threads are faster. Oversubscription is a diagnostic experiment, not a recommended default. Select the best sustainable configuration, not the single fastest short burst. Re-run after the device reaches a stable thermal state.

## Phase 2 — Asynchronous lifecycle and hot-swap hardening

### 2.1 Measure unload / swap / load separately

Instrument monotonic timestamps for:

- request received;
- generation cancellation acknowledged;
- native context teardown started/completed;
- old model released;
- replacement file validated;
- new model initialization started/completed;
- warm-up probe completed;
- manager state changed to Ready.

Report unload duration, load duration, warm-up duration, and total swap duration independently. Test same-model idempotent load, cold load, warm load, and replacement-model swap. Do not promise a full 5.73 GB model swap in under three seconds: that target is not a safe assumption on mobile hardware. Use measured results to improve the UI (progress stages, cancellation, and a clear "model is loading" state) rather than hiding the real wait.

For the initial single-model manager, avoid holding both full model contexts resident simultaneously. Validate the replacement artifact before unloading the active model; then unload old context, load replacement, run a small probe, and either commit the selection or restore the previous model. Preserve the previous file on disk for rollback.

### 2.2 Controlled retry policy

A failed JNI call must return a structured error; native exceptions must not escape across JNI or crash the process.

Retry only errors classified as transient and retry-safe. Use a small bounded policy (for example, one retry after a short backoff) and expose each attempt through the Loading state. Do not blindly retry:
- out-of-memory or allocation failure;
- invalid/corrupt GGUF or checksum mismatch;
- unsupported architecture/quantization;
- missing native library or incompatible ABI;
- invalid model parameters;
- deterministic native initialization failures.

For OOM, unload partial allocations and show a clear error with safe suggestions such as reducing context/batch size or closing other apps. For corrupt files, quarantine/delete the staged copy and require re-import. For transient I/O failure, allow one retry. If retry fails, preserve the prior model selection and expose a recoverable error state.

The UI must show "Attempting to load model" only while an actual load/retry is occurring. Do not report Ready until native initialization and a small generation probe succeed.

## Phase 3 — Sampler stability sweep

Keep the initial production values at T=0.95, top-p=0.95, frequency penalty=0.5, presence penalty=0.3, once their native mapping has been verified.

Boundary tests are diagnostics, not new defaults:
- Temperature: test 0.01, 0.95, and 1.5.
- Top-p: test the pinned runtime's valid low/high boundaries plus 0.95.
- Frequency/presence penalties: test zero, target values, and documented supported upper bounds.
- Include NaN, positive/negative infinity, negative values, and out-of-range values in configuration validation tests.

Clamp or reject invalid settings at the Kotlin boundary before JNI. Never pass NaN/infinity or invalid values into native sampling. Temperature 0.01 is a near-greedy low-temperature case, not a promise of deterministic output; temperature 1.5 is a valid exploratory stress value only if supported by the pinned runtime. Log the requested and effective native values. Change one parameter per experiment and hold seed/prompt/model/context constant.

## Phase 1/2 exit gate

Before proceeding to turn-aware history work and later RAG:

- Pinned llama.cpp builds for arm64-v8a and loads the exact checksum-verified production GGUF on the Pixel.
- A fixed prompt generates non-empty streamed tokens with network disabled.
- Memory checkpoints, context-size limits, and unload cleanup are recorded.
- Thread-count benchmark selects a sustainable setting.
- Lifecycle cancellation, failed load, retry classification, rollback, and hot-swap are tested.
- T/P/FP/PP reach the native sampler and invalid values are rejected safely.
- A sustained run completes without crash, ANR, or OS low-memory kill.

## Execution priority

1. Native runtime and real model load/generation.
2. Memory and thread profiling.
3. GGUFManager lifecycle, retry, and rollback.
4. Sampler injection and boundary tests.
5. Turn-aware Room history.
6. Offline Key Facts ingestion.
7. RAG retrieval (later phase only).

No RAG implementation should begin before the model and lifecycle gates pass on the physical target device.
