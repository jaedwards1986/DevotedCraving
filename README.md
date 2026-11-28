# NEXORA

NEXORA is an Android AI chat project centered on Natasha, a fictional adult AI character with a sophisticated, playful personality and a consistent visual identity.

The first working prototype uses the free, community-powered AI Horde network for remote text generation. The app handles model discovery, asynchronous generation, conversation context, and cancellation. This prototype requires an internet connection and sends prompts to volunteer-operated workers; it is not private local inference or an offline chat implementation.

No paid API or subscription is required for initial testing. AI Horde supports anonymous requests, although a personal key may improve queue priority.

## Current status

- Kotlin and Jetpack Compose Android project scaffold added.
- AI Horde text API client added with model discovery, async submit/status polling, and cancellation.
- Basic chat screen added with model selection, API-key entry, and in-memory conversation context.
- GitHub Actions debug-build workflow added.
- **Build status:** CI run is queued; no APK has been verified or installed yet.
- Persistent history, secure API-key storage, streaming, and device validation remain outstanding.

See [docs/BLUEPRINT.md](docs/BLUEPRINT.md) for the product definition and [docs/AI_HORDE_PROTOTYPE.md](docs/AI_HORDE_PROTOTYPE.md) for the approved prototype direction and its limitations.
