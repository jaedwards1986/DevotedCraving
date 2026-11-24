# ForgeAI Feature Matrix

This matrix records verified repository state separately from desired behavior. A feature is not "complete" merely because its prompt, design, or roadmap entry exists.

Status meanings:
- **Implemented (code):** the relevant application code exists in the current source.
- **Partial:** some supporting code exists, but the acceptance requirement is not met.
- **Not implemented:** no corresponding implementation is present in the current source audit.
- **Not verified:** implementation may exist, but the stated test/device acceptance check has not been performed.

## Natasha persona

| Capability | Status | Evidence / remaining work |
|---|---|---|
| Natasha default identity prompt | Implemented (code) | `AppSettings.DEFAULT_SYSTEM_PROMPT` contains the consolidated identity, proactive voice, continuity, consent, adult-only, accuracy, and memory-honesty instructions. |
| Prompt persists for new/default settings | Implemented (code) | DataStore falls back to `DEFAULT_SYSTEM_PROMPT` when no custom prompt is stored. |
| Existing users receive the new default automatically | Not implemented | Existing saved custom prompts are preserved. Users with an older saved prompt may need to select/save the Natasha template. |
| Persona adherence at or above 90% | Not verified | Requires a documented prompt/model evaluation set and manual scoring against the selected inference model. Prompt wording alone cannot guarantee adherence. |
| Consistent persona across model changes | Not verified | Requires testing across actual supported local model families. |

## Inference and cost

| Capability | Status | Evidence / remaining work |
|---|---|---|
| 100% free, on-device inference | Not implemented | Current chat client uses an OpenAI-compatible HTTP endpoint. |
| Minimal Android JNI bridge health check and UTF-8 round-trip | Implemented (code); device verification pending | CMake builds an arm64-v8a shared library and Settings exposes a manual health-check button that checks Unicode string and integer token-ID array round-trips. This is a bridge diagnostic only, not llama.cpp. |\n| Embedded llama.cpp inference runtime | Not implemented | No llama.cpp library, model context, GGUF loader, or token-generation path is present. |
| Selected Llama 3 8B Instruct GGUF Q5_K_M model | Planned | Model selected; exact downloadable artifact and checksum still need pinning. |\n| CI smoke-test GGUF artifact | Bundled by CI; runtime use not implemented | Workflow currently downloads Qwen3 4B Instruct Q4_K_M and packages it into the APK; app-side loading is not implemented. It is not the selected Llama 3 production target. |
| Temperature 0.95 | Partial | Temperature is configurable and persisted, but the default remains 0.7. |
| Top-P 0.95 | Not implemented | Not present in the current request DTO or native sampler integration. |
| Frequency penalty 0.5 | Not implemented | Not present in the current request DTO or native sampler integration. |
| Presence penalty 0.3 | Not implemented | Not present in the current request DTO or native sampler integration. |
| Offline operation after model download | Not implemented | Cannot be claimed until the embedded runtime and model lifecycle are implemented and exercised offline. |
| Tensor G4 NPU acceleration | Not implemented / not verified | Feasibility assessment says no documented first-party llama.cpp NNAPI backend; NNAPI is deprecated in Android 15. Requires a maintained compatible backend and physical-device proof of actual NPU execution. |
| Pixel Mali-G715 Vulkan acceleration | Not implemented / not verified | Identified as an optional experiment only after CPU inference works. Exact device/driver/model support and sustained speedup have not been demonstrated. |
| Pixel CPU optimization (Arm/KleidiAI) | Planned | CPU is the required baseline. Evaluate the pinned llama.cpp revision's Android KleidiAI support and benchmark on the physical Pixel; no throughput claim exists yet. |

## Conversation history and long-term memory

| Capability | Status | Evidence / remaining work |
|---|---|---|
| Room-backed conversation persistence | Implemented (code) | Chat messages and conversations are stored through Room. |
| Persistence across app restarts | Not verified | Requires an app/device restart test. |
| Bounded recent-history payload | Partial | Repository sends at most the last 40 non-error message records (approximately 20 user/assistant exchanges when each exchange has two messages). |
| Explicit 20-exchange window with correct turn pairing | Not verified | Current implementation uses a message-record limit, not a turn-aware exchange counter. |
| Local long-term memory extraction | Not implemented | No local key-fact extraction pipeline was present in the audited files. |
| Local RAG retrieval and prompt injection | Not implemented | No retrieval/index pipeline was present in the audited files. |
| No-Fake-Memory confidence/status guard | Prompt rule implemented; retrieval guard not implemented | The system prompt directs Natasha not to invent memories, but there is no verified retrieval-confidence status pipeline feeding the prompt. |
| Memory reliability at or above 95% | Not verified | Requires a labeled memory-recall test set, retrieval failure cases, and manual/automated scoring. |

## User experience and release quality

| Capability | Status | Evidence / remaining work |
|---|---|---|
| Dark theme default | Implemented (code) | App settings default to `ThemeMode.DARK`. |
| Dark minimalist visual acceptance | Not verified | Requires UI review and accessibility/contrast checks on device. |
| Feature matrix | Implemented (documentation) | This file records current implementation state and outstanding acceptance checks. |
| End-to-end user journey tests | Not verified | Requires execution across settings, chat, restart, model load, offline use, and memory flows. |
| Pixel 9 Pro XL verification | Not verified | No device-level installation or runtime test is claimed here. |

## Current GO / NO-GO

**NO-GO for release against the stated project criteria.** The persona prompt is committed, but the 90% persona score and 95% memory reliability score have not been measured. The local runtime, sampler integration, RAG pipeline, and offline device verification remain outstanding.

Reassess each row only after its implementation and corresponding test evidence are available.
