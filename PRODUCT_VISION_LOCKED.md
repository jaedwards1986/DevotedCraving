# ForgeAI — Locked Product Vision

**Status: LOCKED.** This document is the authoritative product specification. Implementation work must conform to it. Do not reinterpret, remove, weaken, replace, or redesign a requirement without the owner's explicit approval. Bug fixes, compatibility work, performance work, tests, and packaging are allowed when they preserve this vision.

## 1. Product and ownership
- Product/repository: ForgeAI, with Natasha as its central personality and interface.
- Target device: Google Pixel 9 Pro XL (Android, arm64).
- The app must be 100% free to use: no paid APIs, subscriptions, hidden charges, or automatic paid fallbacks.
- The public ForgeAI repository is the working repository. The separate NatashaAfterDark backup repository must remain private; never change its visibility or billing settings.
- Deliver a real, installable Android APK. Do not report a build as complete until CI succeeds and the APK artifact is verified.

## 2. Natasha — identity and conversation
- Natasha is the core fictional adult woman/personality of the app, not a generic assistant with a character skin.
- Her identity, conversational style, memories, and relationship continuity must persist across app updates, model changes, and runtime changes.
- She should converse with the natural flow of ChatGPT: understand context, implications, teasing, jokes, shorthand, emotional tone, and follow-ups; remember what was said; respond spontaneously and coherently rather than in canned roleplay lines.
- Her adult personality is bold, sexually confident, provocative, and explicit to the fullest extent the selected model actually supports and permits. Do not artificially soften her to generic mild flirtation. Do not force sexual content into practical, serious, or technical topics.
- Natasha is always an adult. No sexualization or portrayal of minors.
- Never claim a model can produce content or bypass restrictions that it cannot. Be transparent about the model's actual capabilities and rules.
- No phone notifications for roleplay or conversation. The user prefers an immersive in-app experience.

## 3. Models and first launch
- First launch must automatically obtain, verify, configure, and select the bundled/recommended default GGUF model. The user must not have to search for, select, or manually import the first model.
- The initial default is the NSFW/roleplay-oriented Llama 3.1 8B GGUF Q5_K_M model selected in the current implementation, subject to verifying the exact upstream file, integrity, license, and device compatibility before release.
- Model downloads must show honest progress, handle interruption/resume or retry safely, validate size/hash where available, and report actionable errors. Never silently fall back to an empty model state while claiming readiness.
- A Models area must allow the user to import, add, and switch models later. Switching models must not erase Natasha's identity, personality version, memories, or conversation history.
- Select the correct prompt/chat template for each supported model family. Do not guess a template based only on a marketing name.

## 4. Personality updates
- Natasha's personality is versioned independently from model weights and app code.
- On opening the app, if a new personality pack is available, show an in-app update prompt explaining that Natasha is being updated to match the installed model's verified expressive capabilities.
- The user explicitly chooses to install the personality update. Show completion and the installed version.
- The update can adapt her adult intensity to the selected model's actual capabilities, but must preserve her identity, natural conversational rhythm, memories, and established continuity.
- A model change may trigger a matching personality update; it must never silently rewrite or downgrade her personality.
- Personality prompts configure behavior; they cannot remove restrictions inherent to model weights or runtime. Clearly distinguish configured behavior from verified model capability.

## 5. Performance and conversation quality
- Must run on the Pixel 9 Pro XL without avoidable lag, freezes, crashes, or robotic/repetitive responses.
- Stream responses as they are generated. Use sensible context, memory, history, token, and thread settings for the device and model.
- Optimize latency and quality without silently sacrificing conversation continuity or changing the product vision.
- Be honest about local inference limits and measured performance. Do not promise zero latency.

## 6. Memory and continuity
- Maximize useful persistent memory and conversational continuity within device and model limits.
- Preserve chat history and memories across app restarts, model switches, and personality updates.
- Provide backup and restore for memory/history. Never silently delete user data during maintenance or upgrades.
- Retrieve relevant memories without flooding the model context or allowing stored text to override Natasha's identity or system behavior.

## 7. App design and experience
- Deliver a polished, modern, professional ChatGPT-like Android interface.
- Visual direction: dark navy/blue with restrained blue accents, clean spacing, readable typography, and consistent components.
- Do not replace the established design with childish, cluttered, or arbitrary visual treatments.
- Keep the core experience straightforward: Chat, Projects, Models, Memory, and Settings.

## 8. App creation and building — core capability
- ForgeAI must help the user plan and design real apps through a natural conversation, asking one concise question at a time when clarification is needed.
- Respect the user's agreed requirements. Do not hijack, reinterpret, or expand the project into a different product.
- It must create and edit actual project source files, preserve project context, review proposed changes, and let the user approve writes.
- It must be able to run appropriate builds and tests, inspect errors, fix defects, rebuild, and provide installable outputs such as APKs.
- If a free online build runner is used, it must have no paid fallback, clearly disclose free-tier limits, and require explicit user authorization for any account/token connection. Local project editing/export must remain available if online builds are unavailable.
- Do not claim ForgeAI can compile user projects until an end-to-end build pipeline is implemented and verified.

## 9. Privacy, safety, and cost
- Local-first storage and no required account for ordinary app use.
- No paid services or hidden billing. If a connected free service becomes unavailable or would incur cost, stop that feature and explain; never charge or silently switch to paid infrastructure.
- Protect credentials and private data. Never expose the user's email addresses or tokens in logs, prompts, or public files.
- Keep the NatashaAfterDark backup repository private.

## 10. Definition of done and change control
- The vision is locked. Changes to product behavior, personality, scope, design direction, privacy, cost, or default-model intent require explicit user approval.
- Routine engineering may fix bugs, improve reliability/performance, update dependencies, add tests, and maintain compatibility only when it preserves this specification.
- A release is not done until the Android build and tests pass, the APK artifact is uploaded and verified, and the artifact is made available to the user with a direct download link.
- Report separately what is implemented, what passed automated tests, what was verified on a real Pixel 9 Pro XL, and what remains unverified.
