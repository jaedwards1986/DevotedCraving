# DavotedCraving — Product Blueprint

## Product identity

DavotedCraving is a free, local-first Android adult-entertainment companion and Natasha Nice fan-archive experience. The app is designed for the Google Pixel 9 Pro XL. Its visual identity is dark black, deep red, and ember orange, with restrained pulse animation, tactile controls, and a launch PIN. The PIN is the app's primary access control; do not add hidden content modes or silently change the user's settings.

The companion is a customizable **fictional adult persona**. It may support adult-oriented conversation within the app's supported content rules, but it must not claim to be Natasha Nice, impersonate her, claim private access to her, or present a simulated private relationship as real. Natasha Nice content belongs in a separate factual fan-archive area, clearly identified as public-source material.

## Core product principles

- Free to use; no paid inference API, subscription, credits, or mandatory remote chat service.
- Local GGUF inference is the default and final chat path. Remote providers, if ever added, must be optional, clearly disclosed, and off by default.
- The app owns persona configuration, preferences, memory, corrections, exclusions, and conversation history. These survive model changes and app restarts.
- Explicit user corrections and exclusions override inferred memories. Memory capture, review, edit, pin, and deletion controls must be available to the user.
- No silent model, feature, or settings changes. Discovery may suggest updates; the user approves installation or activation.
- A successful CI build is not proof of installation or runtime behavior on the Pixel 9 Pro XL. Report those milestones separately.

## Models

Default model candidate: **Llama 3 8B Instruct – Uncensored, Q4_K_S**. Alternative quantization: Q5_K_M.

Testing shortlist (not confirmed as compatible or selected):
- Roleplay-Llama-3-8B
- Qwen3.5 9B NSFW RP
- SageV2 8B NSFW RP
- Dolphin 2.9.3 Mistral 7B
- Hermes 2 Pro Llama 3 8B

Model management must support local import, model metadata, file-size and SHA-256 verification, duplicate-safe naming, cancellation, cleanup after failure, serialized imports, explicit load/unload state, and a clear READY_FOR_CHAT gate. Model compatibility and performance must be tested on the target device before a model is described as supported.

## Long-term memory and companion

Memory is application-owned and model-independent. The target design includes:
- Persistent conversation history and user-approved durable memories.
- Likes, dislikes, preferences, boundaries, recurring topics, important events, and relationship-context continuity for the fictional persona.
- Retrieval of relevant memories into each conversation, with source and confidence metadata.
- Explicit correction and exclusion precedence over inferred or stale memories.
- User controls to inspect, edit, pin, export, and delete stored memories.
- Backup/restore that is local and user-controlled.

Target pipeline: Persona Orchestrator → Memory Service and Preference Index → Experience Controls → Context Assembler → Local Model Adapter → GGUF runtime. Model switching must not reset or rewrite app-owned state.

## Natasha Nice public fan archive

The archive is a source-linked catalog of authentic, publicly available material and media that the app is authorized to index or download. It may include career information, interviews, official/public posts, public appearances, authorized previews, and links to legitimate publisher or creator pages.

Each item should retain provenance fields: SourceID, canonical URL, source name, access level, usage/license status, capture date, and authenticity/verification status. Search and filters may use factual metadata such as date, source, title, publisher, and verified tags.

Do not crawl or redistribute leaked, stolen, hacked, non-consensually shared, or paywalled material; do not bypass access controls, subscriptions, DRM, or platform restrictions. Do not represent reposts or lookalikes as authentic. Prefer official feeds, documented APIs, permitted embeds, and user-submitted links. Downloads are allowed only when the source and rights permit them.

The archive must not imply that Natasha Nice personally uses, endorses, or communicates through DavotedCraving. No fabricated DM status, read receipts, likes, private messages, or direct-contact simulation.

## Experience and interface roadmap

Potential enhancements, subject to device capability and user approval:
- Dark collector-archive home with curated collections, favorites, provenance timeline, and a discovery queue.
- Newest-first updates from supported and permitted public sources; show source and last-checked time.
- User-controlled metadata tagging and search. Automated labels must be marked as machine-generated and reviewable.
- Optional voice output and spatial-audio presentation for app-generated fictional companion dialogue, with clear audio controls.
- Optional visual effects and animation with reduced-motion settings.
- Any 3D wardrobe or AR feature must use licensed/authorized assets and must not fabricate a photorealistic sexual depiction of a real person.

## Monitoring and maintenance

Monitoring must be useful without creating a paid-service dependency:
- **Per build / pull request:** compile, unit tests, dependency checks, APK assembly, artifact integrity, and model-import tests where applicable.
- **Daily:** scheduled CI health check, dependency/security advisory scan, repository workflow status, and checks of configured archive source endpoints where permitted. Record failures and last-success timestamps.
- **Runtime:** an in-app diagnostics page should report local database health, model readiness, storage availability, last successful backup, and last maintenance check. Diagnostics remain on-device by default.
- **Real-time:** report errors and model/runtime failures immediately in the app. Continuous remote uptime monitoring is not guaranteed by a free, local-only app; any external monitoring must be optional, privacy-preserving, and cost-free to operate.
- **Monthly:** discover possible new features, Android compatibility changes, model candidates, and supported archive sources. Present a changelog and proposed changes for user approval; never auto-install or silently alter settings.
- **Before release:** build APK, verify checksum, run tests, and record the exact commit and artifact. Pixel installation, PIN behavior, model inference, memory persistence, and archive behavior require separate device validation.

## Delivery order

1. Keep the Android project building reliably and publish a verifiable debug APK.
2. Complete safe local GGUF import, integrity checks, cancellation, load/unload, and target-device smoke tests.
3. Implement the local chat pipeline and app-owned persistent memory retrieval.
4. Add user controls for persona configuration, memory review, and model switching.
5. Build the source-provenance archive with permitted feeds, links, and authorized media handling.
6. Add scheduled health checks, in-app diagnostics, and monthly user-approved feature discovery.
7. Add optional voice, spatial audio, and visual enhancements after core functionality is stable.

## Acceptance criteria

- The app opens behind the configured PIN and keeps the chosen dark theme.
- Chat generation works offline using a verified local GGUF model.
- Persona settings, conversation history, preferences, and approved memories survive restart and model switching.
- Explicit corrections and exclusions take precedence during retrieval.
- Model import is cancellable, duplicate-safe, integrity-checked, and cleans temporary files after failure.
- Archive records show provenance and access/usage status; unsupported or unverified material is not presented as authentic.
- Daily checks and monthly discovery produce visible status/results, and no update is applied without user approval.
- CI evidence, APK artifact evidence, and Pixel runtime evidence are reported separately.