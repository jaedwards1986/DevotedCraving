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
| D-006 | Visual direction: dark charcoal base with deep violet and electric cyan accents. | Approved starting direction |
| D-007 | Primary navigation: Home, AI Chat, App Builder, Design Studio, Projects. | Approved |
| D-008 | Code Studio placement (inside App Builder or a separate workspace). | Open |
| D-009 | Design the home dashboard first, before implementation. | Approved |
| D-011 | Home dashboard layout: logo at top, central “What do you want to create?” prompt, quick-access tiles for AI Chat, App Builder, Design Studio, and Code Studio, recent projects below, and bottom navigation. | Approved |
| D-012 | On launch, show a welcome screen with the UnBoundAI logo and a choice of what to do, rather than opening directly to the dashboard or chat. | Approved |
| D-010 | Keep all project documentation in this single GitHub document. | Approved |

## 3. Product experience and navigation

### App launch — approved

On opening UnBoundAI, show a welcome screen with the UnBoundAI logo and a choice of what to do next. Do not automatically open the home dashboard or a chat as the initial launch destination. The exact welcome-screen options and layout remain to be designed.

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

## 4. Planned capabilities

- **AI Chat:** local models first, optional free online providers, conversation history.
- **App Builder:** create Android applications from natural-language descriptions.
- **Design Studio:** create and refine interface designs.
- **Code Studio:** write, edit, explain, and debug code.
- **Projects:** save and manage work in one place.
- **Settings:** model management, privacy, appearance, and data management.

These are product goals, not claims that the capabilities are already implemented.

## 5. Technical direction

- Platform: Android.
- UI: Kotlin and Jetpack Compose.
- Local inference: GGUF models with an on-device inference runtime such as llama.cpp.
- Persistent project data: local database and app-managed files.
- Offline operation: core local chat and locally stored project data must remain available without internet.
- Online providers: optional integrations only; provider availability, quotas, and terms must be checked before implementation.
- No secrets, API keys, or credentials committed to the repository.

Technical choices may be refined as implementation begins. Record approved changes in the decision history below.

## 6. Roadmap and status

| Stage | Work | Status |
|---|---|---|
| 1 | Product identity and high-level direction | Approved |
| 2 | Home dashboard interface design | Next |
| 3 | Android application foundation and navigation | Not started |
| 4 | Local model management and offline chat | Not started |
| 5 | Projects, persistence, and workspace tools | Not started |
| 6 | App Builder, Design Studio, and Code Studio workflows | Not started |
| 7 | Pixel 9 Pro XL testing and installable APK validation | Not started |

Statuses must be updated only when supported by actual work and test evidence.

## 7. Decision history and work notes

- **2026-11-27:** Project name confirmed as UnBoundAI.
- **2026-11-27:** Product defined as a general-purpose AI assistant and creative development workspace, not an adult gallery.
- **2026-11-27:** User selected the combination of ChatGPT-like simplicity and professional workstation capabilities.
- **2026-11-27:** Dark charcoal with violet and cyan accents selected as the starting visual direction.
- **2026-11-27:** Primary navigation established as Home, AI Chat, App Builder, Design Studio, and Projects.
- **2026-11-27:** Home dashboard selected as the first screen to design.
- **2026-11-27:** Approved the home dashboard starting layout: top logo, central creation prompt, four quick-access tiles, recent projects, and bottom navigation.
- **2026-11-27:** Question 2 — App launch behavior: selected **C, Welcome Screen**. Show the UnBoundAI logo and a choice of what to do; exact choices and layout remain open.
- **2026-11-27:** User requested a single GitHub document as the durable project blueprint and decision record.

### Ongoing change-record format

For each new meaningful item, add a dated entry with:
- **Question or topic**
- **Options considered** (when relevant)
- **User's decision**
- **Reason or constraints**
- **Status:** Approved, Open, Implemented, Verified, or Superseded

When a decision changes, retain the old entry as Superseded and record the new decision. Never silently rewrite project history.

## 8. Working instruction

Before continuing UnBoundAI work after a break, read this entire document. Treat approved decisions as the current requirements, open items as undecided, and roadmap statuses as claims that require evidence. Keep all future project documentation in this README so the user can simply say:

**“Go read the UnBoundAI blueprint on GitHub.”**
