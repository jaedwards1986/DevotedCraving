# Pixel 9 Pro XL Accelerator Feasibility — Phase 1 Gate

**Target:** Google Pixel 9 Pro XL, Tensor G4, 16 GB RAM, Android arm64-v8a  
**Purpose:** Decide which llama.cpp execution backends are technically justified before adding accelerator-specific code.  
**Status:** Source/documentation feasibility assessment only. No llama.cpp runtime is integrated, and no Pixel device benchmark has been run.

## Executive decision

1. **CPU is the required baseline and first implementation target.** Build llama.cpp for Android arm64-v8a with its portable CPU path. Evaluate Arm KleidiAI kernels where supported by the pinned llama.cpp revision. Do not set a global CPU architecture flag that could make the APK incompatible with other arm64 devices.
2. **Vulkan is the only GPU candidate worth a controlled Pixel experiment in the first pass.** The Pixel 9 Pro XL uses an Arm Mali-G715 GPU. llama.cpp has a Vulkan backend, but upstream Android documentation does not establish that this exact Mali device + driver + selected Llama 3 Q5_K_M workload is validated. Treat it as experimental until the backend enumerates the GPU, offloads supported layers, produces correct tokens, and beats CPU in sustained tests.
3. **Do not prioritize OpenCL for this device.** llama.cpp's documented OpenCL backend is aimed primarily at Qualcomm Adreno GPUs and its verified Android device list is Snapdragon/Adreno. Pixel 9 Pro XL is Mali, not Adreno.
4. **Do not make NNAPI the first accelerator path.** Android deprecated the NNAPI NDK API in Android 15. Current Android guidance points developers toward updated LiteRT/TensorFlow Lite paths for supported model deployments. llama.cpp does not expose a documented first-party NNAPI backend in its current backend list. A TFLite/LiteRT delegate is not a drop-in execution backend for llama.cpp GGUF graphs; it would require a separate conversion/runtime integration and operator/quantization validation. Revisit only if a maintained, compatible adapter is identified.
5. **The Tensor G4 NPU is not automatically available to arbitrary JNI code.** Do not claim NPU use from the presence of a Tensor chip, NNAPI device enumeration, GPU usage, or a model running locally. Require runtime/backend logs and device profiling that identify the actual accelerator and confirm meaningful graph execution on it.

## Hardware facts and constraints

- Google identifies the Pixel 9 Pro XL as a Tensor G4 device with 16 GB RAM.
- Published device specifications identify an Arm Mali-G715-class GPU; confirm exact renderer, driver, Vulkan API version, extensions, and compute limits on the physical handset at runtime.
- The handset's 16 GB is shared with Android, graphics, and other apps. It is not an application memory budget.
- The selected production model remains Meta Llama 3 8B Instruct GGUF Q5_K_M. No accelerator experiment may silently substitute the Qwen3 CI fixture or change the production model.
- Tensor G4 CPU acceleration (including Arm-specific optimized kernels) is distinct from GPU offload and distinct from NPU execution.

## Backend feasibility matrix

| Path | Pixel 9 Pro XL assessment | Decision |
|---|---|---|
| llama.cpp CPU | Android arm64-v8a build path is documented. Portable CPU kernels are the baseline; test Arm KleidiAI support in the pinned revision. | **Implement first** |
| llama.cpp Vulkan | Backend exists upstream. Mali-G715 exact-device support and Llama 3 Q5_K_M performance are not established by the documentation reviewed. Driver support, build, offload, correctness, and sustained speed are unknown. | **Build as an optional experiment after CPU works** |
| llama.cpp OpenCL | Upstream OpenCL documentation focuses on Adreno and lists Snapdragon Android validation, not Pixel Mali. | **Do not target initially** |
| NNAPI | Android API deprecated in Android 15; no documented first-party llama.cpp NNAPI backend found. | **Defer / no-go absent maintained adapter** |
| LiteRT / TensorFlow Lite delegate | Android's recommended migration direction for supported ML workloads, but not a drop-in backend for llama.cpp's GGUF graph and sampler. | **Separate architecture decision only** |
| Tensor G4 NPU / AICore | Availability to arbitrary third-party llama.cpp JNI workloads is not established. | **Unverified; no NPU claims** |

## Implementation sequence

### A. Establish the CPU reference
- Pin a specific llama.cpp commit and record build options, license, and source provenance.
- Build arm64-v8a with GGML_NATIVE=OFF for cross-compilation. Evaluate the revision's Android KleidiAI option only if it is present and builds cleanly; keep a portable CPU build as a fallback.
- Keep CPU prompt-processing and generation thread counts independently configurable when supported.
- Load the exact checksum-pinned Llama 3 8B Instruct Q5_K_M artifact. Confirm GGUF metadata and tensor types with the parser; do not infer quantization from a filename alone.
- Run deterministic correctness tests and a sustained baseline before introducing GPU code.

### B. Probe Vulkan without making it a dependency
- Build a second arm64-v8a variant with the pinned revision's Vulkan backend enabled, keeping the CPU build available.
- At runtime record Vulkan physical-device name/vendor, driver version, API version, extensions, compute queue availability, backend initialization, selected device, offloaded layer count, and any fallback reason.
- Test small controlled offload values (including CPU-only and increasing layer counts). Confirm the backend really offloads the expected operations; merely compiling Vulkan or enumerating a device is not a successful inference test.
- Run the same model, checksum, prompt set, context, batch, sampler, seed, output-token cap, and thermal starting condition as CPU.
- Reject the Vulkan path if it crashes, silently falls back, emits incorrect output, exceeds safe memory, or fails to improve sustained end-to-end throughput/latency.

### C. NNAPI / NPU reconsideration gate
Only reopen this path if all of the following are supplied:
- a maintained adapter/backend that explicitly supports the chosen llama.cpp revision and Android arm64;
- proof that it can execute the relevant GGML operations and Q5_K_M tensor formats without unsupported-op fallback that erases the benefit;
- documented Pixel 9 Pro XL / Tensor G4 driver support;
- a reproducible build and runtime diagnostic showing the actual NPU device is executing graph operations;
- correctness, memory, thermal, and sustained benchmark results against CPU and Vulkan.

A generic NNAPI API availability check, TFLite GPU delegate availability, AICore availability, or successful CPU fallback does not pass this gate.

## Benchmark protocol

For each viable backend (CPU, then Vulkan if viable):
- Record exact app build/commit, llama.cpp commit, model repository revision, filename, byte size, SHA-256, GGUF metadata, device model, Android build, backend, driver, context, batch, sampler, prompt hash, seed, output cap, and thread/offload settings.
- Run a cold-load measurement separately from warm inference.
- Use a fixed prompt suite and fixed output token cap. Include short and longer prompt contexts so prompt processing and token generation are not conflated.
- Warm up before collecting results; then run at least 100 consecutive requests for endurance only after single-request correctness and memory safety pass.
- Capture per request: prompt processing duration, time to first token, generated tokens, generation duration, tokens/sec, end-to-end duration, errors/cancellation, and memory checkpoints.
- Report median, p90, minimum, maximum, and range; do not report only a grand average.
- Capture thermal status and CPU/GPU frequency/utilization where accessible. Repeat under sustained thermal conditions; a short peak-speed run is not the selected configuration.
- Calculate speedup as CPU elapsed time divided by accelerator elapsed time for the same workload and metric. Report prompt-processing and generation speedups separately.
- Keep device plugged/unplugged and battery/thermal conditions consistent and recorded.

## Memory and quantization notes

- Process PSS is process-wide and must not be labeled as llama.cpp-only memory. Record native heap, Java heap, file-backed mappings, runtime-reported allocations, and process PSS/RSS as separate views.
- mmap does not mean model data is outside RAM; resident file-backed pages still contribute to process memory accounting. Record mapped bytes and resident/PSS separately.
- Q5_K_M is a mixed tensor quantization format. Validate the actual GGUF metadata and per-tensor types through the parser. A filename/config mismatch should be detected and rejected; never assume it merely causes garbage tokens.
- Do not begin 100-request endurance runs until model loading, short inference, cancellation, and unload are stable.

## Exit criteria for the accelerator decision

- CPU baseline builds, loads the pinned model, and produces correct streamed output.
- Vulkan is either demonstrated correct and measurably beneficial on the physical Pixel, or explicitly marked unsupported/not beneficial for this release.
- NNAPI/NPU remains disabled unless the separate reconsideration gate passes.
- The chosen production backend is selected from measured sustained results, not presumed hardware capability.
- No throughput, RAM, or acceleration ratio is published until measured on the physical Pixel 9 Pro XL.

## Sources reviewed

- llama.cpp Android build and CPU/KleidiAI guidance: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp build/backend documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- llama.cpp OpenCL backend hardware scope: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- Android NNAPI deprecation and migration guidance: https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- Google Pixel 9 family Tensor G4 and 16 GB RAM announcement: https://blog.google/products-and-platforms/devices/pixel/google-pixel-9-pro-xl/
