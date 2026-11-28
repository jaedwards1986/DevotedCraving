# NEXORA — Fresh Build Blueprint

## Product
NEXORA is one unified, free Android product with a fictional adult companion and a Natasha Nice public archive. Both share the app identity, design system, settings, and private library.

The companion is fictional. It must not claim to be Natasha Nice, speak for her, or imply access to her private communications or life. The archive is a provenance-first index of authentic public material only.

## Non-negotiable architecture
- Local-first; no paid APIs, subscriptions, ads, mandatory accounts, or remote chat inference.
- llama.cpp with user-selected GGUF models is the intended inference path.
- Persona, preferences, conversations, memory, and settings belong to NEXORA, not the active model.
- Model changes must preserve all app-owned state.
- Explicit user corrections and exclusions override inferred memories.
- Internet use is opt-in for model downloads and public archive discovery. No silent downloads or updates.
- No fabricated archive media, private material, or bypassing access controls.

## Device and model
- Primary device: Google Pixel 9 Pro XL.
- Accepted initial model candidate: Llama 3 8B Instruct Uncensored Q4_K_S, subject to licensing and actual device testing.
- Q5_K_M is an alternative if memory and speed permit.

## Visual design
Dark cinematic appearance: near-black backgrounds, restrained crimson/burgundy, antique-gold details, warm highlights. Main destinations: Companion, Archive, Library, Settings. Chat is the primary screen.

## Milestones
1. Fresh Android project and reproducible debug APK.
2. Secure local launch PIN and privacy controls.
3. llama.cpp runtime, GGUF import/validation, streaming and cancellation.
4. Durable app-owned conversations and memory with review/correction/deletion.
5. Source-verified archive index, provenance, search, favorites, and permitted offline storage.
6. Pixel 9 Pro XL installation, inference, thermal, memory, and restart validation.

## Acceptance rule
CI success confirms compilation and packaging only. Do not call local inference, memory continuity, archive ingestion, or Pixel compatibility complete without direct tests.
