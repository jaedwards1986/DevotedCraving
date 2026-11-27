# NEXORA — Project Blueprint

**Status:** Initial product definition  
**Last updated:** 2026-11-27  
**Purpose:** Define the new NEXORA project from a clean start. This document replaces all prior project concepts and requirements.

## 1. Product identity

NEXORA is an Android application centered on one fictional adult AI character: **Natasha**. The product should feel cohesive and character-led, not like a generic assistant with a character mode bolted on.

The user wants an adult, highly flirtatious, provocative personality with a consistently suggestive tone across ordinary conversation and practical interactions. Keep responses coherent, useful, and context-aware. Do not promise graphic sexual output or capabilities beyond the model and applicable product boundaries.

## 2. Natasha — approved character brief

### Personality and voice
- Sophisticated, elegant, seductive, sultry, confident, and deliberately playful.
- Naughty, provocative, and comfortable with adult language within applicable boundaries.
- Speaks softly and deliberately; avoid robotic, corporate, or exaggerated cartoon delivery.
- The adult, flirtatious undertone is part of her default personality, not a separate mode.
- She has opinions and initiative, while remaining attentive to context and the user's direction.

### Visual identity
- A glamorous, high-end aesthetic with refinement and elegance; never tacky or trailer-trash.
- Fictional adult woman.
- Brunette with naturally curly hair.
- Naturally full breasts.
- Chubby, full-figured “mom bod,” with a genuinely round, soft, visible belly.
- Rounded waist and hips; full thighs and arms.
- Natural proportions and natural-looking beauty. Do not slim, tone, flatten, or airbrush her body.
- Keep her recognizable appearance consistent across profile, chat, and any future gallery or visual surfaces.

### Relationship and continuity
The user wants a familiar, ongoing conversational relationship that can develop over time. The exact relationship mechanics and memory controls remain to be designed.

## 3. Product constraints

- Android application.
- Core experience must work locally and offline after setup.
- No required paid services, subscriptions, paid API keys, remote inference, or external accounts.
- Local model inference is the intended chat path. GGUF/llama.cpp is a candidate implementation, not yet verified or locked.
- Internet may be used optionally to obtain model files, but the app must not depend on connectivity for normal conversation once a model is installed.
- No silent model or application updates; update behavior and user approval flow remain to be specified.
- Target Android device and minimum OS version have not yet been reconfirmed for this new project.
- Prior repositories, architectures, app names, source code, and blueprints are superseded. Do not carry them forward without the user's explicit reintroduction.

## 4. Product experience — initial direction

NEXORA should be straightforward and uncluttered. Natasha is the primary experience. Avoid adding unrelated workspaces, feature collections, or complicated navigation before the core chat works.

Initial product priorities:
1. A clean launch and chat experience.
2. Natasha's consistent character presentation and voice.
3. Local model selection, installation, and readiness state.
4. Local conversation history.
5. Persistent character and user-preference continuity, with transparent user controls.
6. Reliable Android packaging and installation.

Detailed screen layouts, branding, color palette, typography, onboarding, and navigation are open decisions.

## 5. Technical direction — proposed, not yet implemented

A practical first architecture to evaluate:
- Android UI: Kotlin and Jetpack Compose.
- Local inference: evaluate llama.cpp with GGUF models on supported Android hardware.
- Model storage: app-managed local storage, with import/download progress, integrity checks, cancellation, and clear storage controls.
- Conversation storage: local database with explicit retention and deletion behavior.
- Character definition: app-owned Natasha persona configuration, independent of the selected model.
- Generation layer: a model adapter that formats conversation context using the selected model's chat template.
- Network: optional model acquisition only; no remote inference dependency.

These are starting proposals, not verified implementation decisions. Choose dependencies and device requirements only after confirming the target hardware and validating a minimal local inference build.

## 6. Build sequence

### Phase 0 — clean foundation
- Replace the previous repository contents with this NEXORA blueprint.
- Establish a minimal Android project from a clean slate.
- Confirm target device and Android version.
- Add a basic build workflow and produce a debug APK.

### Phase 1 — working local chat
- Integrate a compatible local inference runtime.
- Import one small, suitable GGUF model.
- Validate model loading, prompt formatting, token streaming, cancellation, and repeat conversations on the target device.
- Keep inference entirely on-device.

### Phase 2 — Natasha experience
- Implement the approved character brief as app-owned configuration.
- Build the chat UI and profile presentation.
- Preserve persona configuration across model changes.
- Add local chat history and basic settings.

### Phase 3 — continuity and refinement
- Add user-controlled persistent preferences and conversation continuity.
- Add model management, diagnostics, and storage controls.
- Refine visual identity and accessibility after the core flow is reliable.

## 7. Acceptance criteria

The first meaningful milestone is not a mockup. It is an installable Android build that:
- launches reliably;
- allows a GGUF model to be installed or imported;
- runs a prompt locally without a network connection;
- displays generated output in the chat;
- supports a second turn using conversation context;
- can stop an active generation;
- preserves chat history after app restart.

A successful CI build alone does not prove device compatibility. Final validation must occur on the confirmed target device.

## 8. Open decisions

- Target Android device and minimum Android version.
- Preferred default model and acceptable model size/quantization.
- Whether model files are imported by the user or downloaded from a public model host.
- App lock/PIN requirements.
- Memory scope, review/edit/delete controls, and retention.
- Final logo, colors, typography, and chat layout.
- Whether Natasha has a profile image, and the exact image-generation approach.
- Update and backup behavior.

## 9. Working rules

- Treat this document as the current NEXORA source of truth.
- Distinguish approved decisions from proposals and verified implementation.
- Do not claim a feature is built or tested without evidence.
- Do not reintroduce prior project requirements unless the user asks.
- Keep scope focused on a working local Android chat before adding extras.
