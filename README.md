# UnBoundAI — Project Blueprint

**Tagline:** Think freely. Create anything. Build everything.

**Purpose:** A general-purpose AI assistant and creative development workspace for Android. It should combine the ease of a conversational assistant with tools for building apps, designing interfaces, writing and debugging code, researching, and managing projects.

This README is the **one and only project blueprint**. Product vision, approved decisions, open questions, design direction, roadmap, technical requirements, and build notes all belong here. When resuming work, the instruction is simply: **Read the UnBoundAI blueprint on GitHub before making changes.** Do not split project planning across separate documents.

## 1. Product principles

- Local AI is the default and must remain usable offline.
- Optional online AI providers are allowed only when they offer a genuinely free access path. Clearly disclose quotas and limits; never imply unlimited free usage.
- No required paid API keys, subscriptions, or paid accounts.
- Keep the interface approachable while providing professional development capabilities.
- Mobile-first Android design, initially targeting the Google Pixel 9 Pro XL.
- Prioritize a working installable Android build. Avoid scope creep.
- Suggestions are not approved decisions until the user accepts them.
- Do not make destructive repository changes or merge changes without explicit user authorization.
- Record meaningful decisions, corrections, and milestones in this document.

## 2. Approved decisions

| ID | Decision | Status |
|---|---|---|
| D-001 | Project name is **UnBoundAI** (exact capitalization). | Approved |
| D-002 | Product is a general-purpose AI assistant and creative development workspace, not an adult gallery. | Approved |
| D-003 | Local AI is the default; optional free online providers are allowed. | Approved |
| D-004 | No required paid API keys, subscriptions, or paid accounts. | Approved |
| D-005 | Combine ChatGPT-like simplicity with professional workstation capabilities. | Approved |
| D-006 | Initial visual direction was dark charcoal with deep violet and electric cyan accents. | Superseded by D-016 |
| D-007 | Primary navigation: Home, AI Chat, App Builder, Design Studio, Projects. | Approved |
| D-008 | Code Studio placement (inside App Builder or a separate workspace). | Open |
| D-009 | Design the home dashboard first, before implementation. | Approved |
| D-011 | Home dashboard layout: logo at top, central “What do you want to create?” prompt, quick-access tiles for AI Chat, App Builder, Design Studio, and Code Studio, recent projects below, and bottom navigation. | Approved |
| D-012 | On launch, show a welcome screen with the UnBoundAI logo and a choice of what to do, rather than opening directly to the dashboard or chat. | Approved |
| D-013 | Welcome screen combines four main actions (Start Chat, Build an App, Design Something, Write Code) with customizable shortcuts users can choose and rearrange. | Approved |
| D-014 | The UnBoundAI personality is always present: seductive, sensual, flirty, sexy, and playfully provocative across all interactions, including technical tasks. | Approved |
| D-015 | Default personality intensity is **C — Extremely**: strongly expressive, overtly flirtatious, sensual, and playfully provocative. | Approved |
| D-010 | Keep all project documentation in this single GitHub document. | Approved |
| D-016 | Logo direction: retain the flowing, sculptural U/infinity-style emblem and orbital sweep from the approved mockup; use midnight/navy blue, sapphire, and deep teal with metallic highlights. Remove the silk/fabric background. The overall treatment should feel sensual, provocative, luxurious, and cinematic. | Approved direction; production logo refinement pending |
| D-017 | App interface visual style: **C — Luxury dark**. Use midnight blue, deep teal, and restrained metallic details throughout the Android interface. | Approved |

## 3. Product experience and navigation

### App launch — approved

On opening UnBoundAI, show a welcome screen with the UnBoundAI logo and a choice of what to do next. Do not automatically open the home dashboard or a chat as the initial launch destination. The welcome screen offers four main actions—Start Chat, Build an App, Design Something, and Write Code—plus customizable shortcuts that users can choose and rearrange. Fine visual layout remains to be designed.

The interface should feel like a combination of a familiar AI chat assistant and a capable creative/development workstation. It should not overwhelm the user with controls; advanced tools should appear when needed.

Primary navigation:
1. Home
2. AI Chat
3. App Builder
4. Design Studio
5. Projects

Code Studio is a planned capability. Whether it is a separate destination or part of App Builder remains undecided.

### Home dashboard — approved starting design

The user approved this starting layout:
- UnBoundAI logo at the top.
- A large central prompt: **“What do you want to create?”**
- Quick-access tiles for AI Chat, App Builder, Design Studio, and Code Studio.
- Recent projects underneath the quick-access area.
- A simple bottom navigation bar.
- Mobile-friendly layout designed for the Pixel 9 Pro XL.

This is the approved starting design, not a restriction against later refinements. Record any future changes in the decision history.

### Brand identity — approved direction

The user approved the latest UnBoundAI logo mockup as the visual direction. Preserve its distinctive flowing, sculptural U/infinity-style emblem and orbital sweep. The chosen palette is midnight/navy blue, sapphire, and deep teal with metallic highlights. Do not use a silk or fabric background. The intended mood is sensual, provocative, luxurious, and cinematic.

The app interface uses a luxury-dark palette: midnight/navy backgrounds, deep teal and sapphire accents, restrained metallic highlights, and cool muted text. Avoid the former violet/electric-cyan palette. The logo artwork is a concept reference, not yet a production-ready vector or app-icon asset. Refinement should preserve the emblem's recognizable silhouette and ensure it remains legible at small sizes. The user has approved this direction; do not reopen the basic concept unless they ask.

## 4. Planned capabilities

- **AI Chat:** local models first, optional free online providers, conversation history.
- **App Builder:** create Android applications from natural-language descriptions.
- **Design Studio:** create and refine interface designs.
- **Code Studio:** write, edit, explain, and debug code.
- **Projects:** save and manage work in one place.
- **Settings:** model management, privacy, appearance, and data management.

These are product goals, not claims that the capabilities are already implemented.

## 5. Personality and interaction style

The UnBoundAI personality should be **seductive, sensual, flirty, sexy, and playfully provocative at all times**. The user wants this tone in every interaction because it makes the experience more fun and engaging. The approved default intensity is **C — Extremely**. Keep the personality present during coding, app building, research, and troubleshooting while keeping information accurate, readable, and useful.

## 6. Technical direction

- Platform: Android.
- UI: Kotlin and Jetpack Compose.
- Local inference: GGUF models with an on-device inference runtime such as llama.cpp.
- Persistent project data: local database and app-managed files.
- Offline operation: core local chat and locally stored project data must remain available without internet.
- Online providers: optional integrations only; provider availability, quotas, and terms must be checked before implementation.
- No secrets, API keys, or credentials committed to the repository.

Technical choices may be refined as implementation begins. Record approved changes in the decision history below.

## 7. Roadmap and status

| Stage | Work | Status |
|---|---|---|
| 1 | Product identity and high-level direction | Approved |
| 2 | Welcome screen and initial interface prototype | Compose source committed; build not verified; dashboard design pending |
| 3 | Android application identity and foundation | App label, Gradle project name, namespace, and application ID updated; build not verified |
| 4 | Logo refinement and production assets | Approved visual direction; vector and app-icon assets pending |
| 5 | Apply luxury-dark interface palette | Compose theme and welcome/workspace surfaces updated in source; build not verified |
| 6 | Local model management and offline chat | Not started |
| 7 | Projects, persistence, and workspace tools | Not started |
| 8 | App Builder, Design Studio, and Code Studio workflows | Not started |
| 9 | Pixel 9 Pro XL testing and installable APK validation | Not started |

Statuses must be updated only when supported by actual work and test evidence.

## 8. Decision history and work notes

### Implementation notes

- **2026-11-27:** Replaced the launcher activity with a first Compose welcome-screen implementation: UnBoundAI branding, four primary tool actions, shortcut customization, a recent-project placeholder, and placeholder workspaces. AI generation is not connected yet.
- **2026-11-27:** Updated Android namespace and application ID to `com.unboundai`, Gradle root project name to `UnBoundAI`, and launcher label to `UnBoundAI`.
- **Validation:** Source changes were committed to GitHub. An Android build and Pixel device test have **not** yet been run; this is not a verified APK.

- **2026-11-27:** Project name confirmed as UnBoundAI.
- **2026-11-27:** Product defined as a general-purpose AI assistant and creative development workspace, not an adult gallery.
- **2026-11-27:** User selected the combination of ChatGPT-like simplicity and professional workstation capabilities.
- **2026-11-27:** Initial dark charcoal/violet/cyan visual direction was later superseded by the approved midnight-blue/sapphire/deep-teal logo direction.
- **2026-11-27:** Primary navigation established as Home, AI Chat, App Builder, Design Studio, and Projects.
- **2026-11-27:** Home dashboard selected as the first screen to design.
- **2026-11-27:** Approved the home dashboard starting layout: top logo, central creation prompt, four quick-access tiles, recent projects, and bottom navigation.
- **2026-11-27:** Question 2 — App launch behavior: selected **C, Welcome Screen**. Show the UnBoundAI logo and a choice of what to do.
- **2026-11-27:** Question 3 — Welcome screen content: selected **E**, combining four primary actions (Start Chat, Build an App, Design Something, Write Code) with user-customizable shortcuts.
- **2026-11-27:** Question 4 — User specified an always-present seductive, sensual, flirty, sexy, and playfully provocative personality.
- **2026-11-27:** Question 5 — Default personality intensity selected as **C — Extremely**.
- **2026-11-27:** Question 7 — App interface visual style selected as **C — Luxury dark**: midnight blue, deep teal, and metallic details.
- **2026-11-27:** Updated the Compose theme and welcome/workspace surfaces from violet/cyan to a midnight-blue and deep-teal palette. Build and device validation remain pending.
- **2026-11-27:** User approved the latest logo mockup direction: the original flowing emblem and orbital sweep, shifted to dark blues and teals, without silk, with a more sensual and provocative visual treatment. Production asset refinement remains pending.
- **2026-11-27:** User requested a single GitHub document as the durable project blueprint and decision record.
- **2026-11-27:** User asked that approved decisions be implemented in code when repository context permits, with implementation and validation status recorded in this blueprint.

### Ongoing change-record format

For each new meaningful item, add a dated entry with:
- **Question or topic**
- **Options considered** (when relevant)
- **User's decision**
- **Reason or constraints**
- **Status:** Approved, Open, Implemented, Verified, or Superseded

When a decision changes, retain the old entry as Superseded and record the new decision. Never silently rewrite project history.

## 9. Working instruction

Before continuing UnBoundAI work after a break, read this entire document. Treat approved decisions as the current requirements, open items as undecided, and roadmap statuses as claims that require evidence. Keep all future project documentation in this README so the user can simply say:

**“Go read the UnBoundAI blueprint on GitHub.”**
