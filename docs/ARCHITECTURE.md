# NEXORA Architecture Direction

## Product principle

NEXORA owns the user's persistent application state. Models generate responses; they do not own memory, character definitions, chat history, or settings.

## Current runtime

- Android UI: Kotlin and Jetpack Compose
- Model format: GGUF
- Native inference: llama.cpp Android JNI binding
- Initial ABI: arm64-v8a
- Model storage: app-private local storage
- Network: no remote chat inference; model discovery/downloads may be added separately

The current native binding is an initial integration. Its build and behavior must be verified in CI and on a physical Pixel before it is treated as production-ready.

## Planned modules

1. **Model Manager** — import, validate, store, select, and unload GGUF models.
2. **Local Inference Adapter** — load a model through the native runtime and stream generated tokens.
3. **Chat** — persistent conversations, message editing, regeneration, branching, and export.
4. **Characters** — character cards, greetings, example dialogue, scenarios, and group conversations.
5. **Memory and Continuity** — persistent history and user-controlled memory independent of the selected model.
6. **Prompt Controls** — visible, editable prompt components and generation settings.

## Current limitations

- No persistent database for chat messages yet.
- No character card editor/importer yet.
- No durable memory service yet.
- Model runtime integration has not yet been validated on Pixel hardware.
