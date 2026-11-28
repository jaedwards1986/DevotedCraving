# NEXORA Architecture Direction

## Product principle

NEXORA owns the user's persistent application state. Models generate responses; they do not own memory, character definitions, chat history, or settings.

## Runtime direction

- Android UI: Kotlin and Jetpack Compose
- Model format: GGUF
- Inference runtime: llama.cpp, integrated locally on Android
- Storage: app-private local storage and a persistent database
- Network: optional model discovery and downloads only; no remote chat inference

## Planned modules

1. **Model Manager** — import, validate, store, select, and unload GGUF models.
2. **Local Inference Adapter** — load a model through the native runtime and stream generated tokens.
3. **Chat** — conversations, message editing, regeneration, branching, and export.
4. **Characters** — character cards, greetings, example dialogue, scenarios, and group conversations.
5. **Memory and Continuity** — persistent history and user-controlled memory independent of the selected model.
6. **Prompt Controls** — visible, editable prompt components and generation settings.

## Current implementation status

The repository reset establishes only the Android/Compose starter shell and build workflow. The modules above are planned, not yet implemented. A successful CI build will not be treated as proof of on-device inference or Pixel performance.
