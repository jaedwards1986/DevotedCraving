# AI Horde prototype decision and status

**Date:** 2026-11-27

## Prototype direction

The first NEXORA Android prototype will use AI Horde as an optional remote text-generation backend. This is a deliberate prototype direction and supersedes the blueprint's local-only inference requirement for this milestone only.

- No paid APIs or subscriptions are planned.
- The anonymous key can be used for initial testing; a personal key can be entered by the user.
- The app submits text requests to `POST /api/v2/generate/text/async`, polls `GET /api/v2/generate/text/status/{id}`, and sends DELETE to that status route when generation is cancelled.
- Active text models are discovered from `GET /api/v2/status/models?type=text`.
- Conversation history is assembled in the Android app and sent as prompt context.
- The first milestone is text only. Image generation is out of scope.
- This backend requires internet access and sends prompt content to volunteer-operated workers. It is not offline or equivalent to local/private inference.

## Initial implementation

The repository now contains a Kotlin/Jetpack Compose Android project scaffold and a first AI Horde client implementation. This is source code only; it has not yet been compiled, installed, or validated on a Pixel device.

## Known prototype limitations

- Chat history currently exists only in memory and is lost when the process is restarted.
- API key entry is currently held in screen state and is not persisted.
- Model selection is available, but availability and queue times can change between discovery and submission.
- The client uses periodic status polling and a bounded timeout.
- No streaming output, image generation, account registration, or memory system is implemented yet.
- Device build and real-worker integration tests remain outstanding.
