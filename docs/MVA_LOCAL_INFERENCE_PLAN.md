# Minimum Viable Architecture: Local Inference Proof of Concept

## Objective

Replace the remote inference dependency with an embedded, on-device inference path, while preserving ForgeAI's existing chat UI, Natasha system prompt, and Room-backed conversation history.

The first milestone proves the engine and data path. It does not claim production readiness, model quality, Tensor G4 NPU acceleration, or release readiness.

## Non-negotiable constraints

- Inference runs on the Android device. No hosted model API, API key, subscription, usage credit, or external inference server is required.
- Internet access may be used to obtain runtime/model assets. Once assets are present, loading and generation must work with network access disabled.
- Keep the selected production target: Meta Llama 3 8B Instruct, GGUF Q5_K_M. A smaller GGUF may be used temporarily as a smoke-test fixture only; it must not silently replace the selected target.
- Keep model files in app-private storage and verify their integrity before loading.
- Do not claim NPU use unless a supported backend is identified and device profiling proves it. CPU execution is an acceptable first proof.
- Keep the existing remote client isolated during development; do not route the default chat path to localhost or remove the current path until local inference is actually loadable and tested.

## Architecture boundary

```
Chat UI / ChatViewModel
        |
        v
ChatRepository
  |             |
  |             +--> Room: load recent messages / persist new messages
  |
  v
InferenceEngine interface
        |
        v
LlamaCppEngine (Kotlin/JNI boundary)
        |
        v
llama.cpp native library (arm64-v8a)
        |
        v
GGUF model in app-private files
```

The repository should depend on an inference abstraction, not directly on JNI or llama.cpp types. The initial implementation can expose a suspendable streaming generation API and a cancellation handle. Native calls must not block the Android main thread. Ensure native context/model lifetime is explicit and serialized where required; do not share one mutable context across concurrent generations.

## Priority 1 — Core engine integration

### Implementation sequence

1. Pin a specific llama.cpp release/commit and record its license and upstream URL. Avoid tracking a moving branch.
2. Add the Android NDK/CMake build for `arm64-v8a` first. Add other ABIs only when they are an explicit target.
3. Build and package the native library through the existing Gradle build. Keep native build flags and backend selection documented.
4. Add a small JNI surface for:
   - runtime/build information;
   - model load and unload;
   - one-shot or streamed token generation;
   - cancellation;
   - structured error reporting.
5. Add a Kotlin `InferenceEngine` interface and a `LlamaCppEngine` implementation. Keep model loading and generation off the main thread.
6. Use a small, legally redistributable GGUF smoke-test model with a compatible chat template. Record its exact source, license, file size, and SHA-256. Do not commit multi-gigabyte model weights to the repository.
7. Keep the selected Llama 3 8B Q5_K_M model as the production target; pin its exact artifact and checksum in the model catalog before distribution.

### Priority 1 acceptance tests

- CI builds the native library for `arm64-v8a` and packages it into a debug APK.
- An instrumentation/device test loads the smoke-test GGUF from app-private storage.
- A fixed prompt produces at least one non-empty generated token and then a clean end-of-generation result.
- An invalid path, corrupt model, out-of-memory condition, and cancellation return controlled errors rather than crashing the app.
- Logs identify the llama.cpp revision, ABI, selected backend, model path (without exposing sensitive user data), and resolved generation parameters.
- A device test succeeds with network access disabled after the test model is already installed.

A successful native compile alone is not a runtime proof. A command-line test on a developer workstation is useful for isolating llama.cpp, but the Android JNI/device test is the acceptance gate for the app.

## Priority 2 — Sampler parameter mapping

Add a typed sampler configuration at the inference boundary. The required initial values are:

| Parameter | Target |
|---|---:|
| Temperature | 0.95 |
| Top-P | 0.95 |
| Frequency penalty | 0.5 |
| Presence penalty | 0.3 |

Map each setting to the pinned llama.cpp sampler API supported by the selected revision. Do not assume OpenAI parameter names map identically to llama.cpp internals; document the exact native mapping and supported ranges. In particular, frequency/presence penalties are distinct from repetition penalty.

### Priority 2 acceptance tests

- Unit tests verify settings-to-native configuration mapping, defaults, bounds, and invalid-value handling.
- A native diagnostic reports the effective values actually passed to the sampler.
- Use a fixed model, prompt, seed, context, and token budget. Change only one parameter per comparison.
- Run a baseline and a changed-value test for temperature, top-p, frequency penalty, and presence penalty independently.
- Confirm that each changed value reaches the native sampler. Output differences are useful behavioral evidence but are not, by themselves, proof of correct parameter plumbing.
- Add a deterministic low-temperature/greedy test where supported to verify repeatability.

## Priority 3 — Room history into local inference

Room persistence already exists in the app. Reuse it; do not create a second history store for the local runtime.

### Data flow

1. Receive the user's message.
2. Load the active conversation's recent messages from Room in chronological order.
3. Exclude failed/error placeholders and avoid duplicating the current user message.
4. Build the model-specific chat template with Natasha's system prompt, recent history, and current user message.
5. Send the formatted prompt to the local engine.
6. Stream generated text to the existing UI.
7. Persist the completed assistant response in Room; on cancellation, preserve a clearly marked partial response only if that behavior is explicitly supported by the existing data model.
8. Reload the conversation after app restart and verify that the next prompt includes the expected recent exchanges.

Initially cap context by a turn-aware recent-exchange limit and the model's actual context-token budget. Do not equate a fixed number of database rows with a fixed number of exchanges. Reserve context space for the system prompt and generation budget.

### Priority 3 acceptance tests

- Automated repository test verifies chronological history assembly, system prompt placement, current-message inclusion exactly once, error filtering, and context truncation.
- End-to-end device test proves: user input -> Room history load -> local inference -> streamed UI display -> assistant response persisted.
- A follow-up prompt correctly references a fact from the immediately preceding exchange.
- Force-stop/relaunch and verify that prior conversation history is restored and included.
- Repeat the full sequence with network access disabled.

## Explicitly deferred

- Long-term memory extraction, embeddings, vector search, and RAG.
- Tensor G4 NPU acceleration and vendor-specific delegates.
- Production model download UX, unless needed to provision the smoke-test model.
- Multi-model management, speculative decoding, and performance tuning beyond basic safety limits.
- Persona-fidelity and memory-reliability percentage claims. Those require separate evaluation sets and scoring after local inference is operational.

## Exit criteria for this MVA

The MVA is complete only when all three priority gates pass on an Android `arm64-v8a` device:

1. A GGUF smoke-test model loads and generates tokens through the embedded JNI engine offline.
2. The four sampler settings are demonstrably passed to the pinned native sampler implementation.
3. Room history flows through local inference and the response is displayed and persisted across an app restart.

Until those checks pass, mark local inference as **in progress / not verified**, and retain the release **NO-GO** status. The MVA is a technical foundation, not evidence that the selected 8B model meets latency, memory, thermal, persona, or product-quality targets on the Pixel 9 Pro XL.
