# Natasha Self-Modification and App Administration

## Product requirement

Natasha should be able to help the owner customize, maintain, and extend ForgeAI itself. This is a real application capability, not roleplay or a claim that she changed something when no change occurred.

The capability must remain compatible with the project's $0, local-first, offline-after-download requirement. No paid API, hosted coding agent, or remote inference service may be required for ordinary administration.

## What Natasha may manage

### Persona and behavior configuration
- Edit and save Natasha's configurable persona prompt and style profile.
- Adjust supported generation settings and explain their effects.
- Create, edit, export, import, and restore user-owned prompt profiles.
- Adjust user-configurable content preferences and app-level filters.
- Preserve the user's chosen adult fictional persona and adult consensual roleplay settings.

### App configuration and feature management
- Inspect the app's feature registry and current feature status.
- Enable, disable, or configure supported features.
- Edit user-owned settings, templates, local knowledge files, and app-managed content.
- Diagnose missing, broken, or partially implemented features.
- Generate a proposed implementation for a new feature and apply it through the controlled update pipeline.
- Maintain a change log and explain what changed.

### Source-code maintenance
- Read the installed app's explicitly exposed, owner-authorized source/configuration workspace.
- Propose source changes, produce a readable diff, run available tests/build checks, and stage an update.
- Apply a source update only after the owner approves the exact change set, unless the owner has explicitly enabled a narrowly scoped automatic-update policy.
- Build a replacement APK locally when the device/toolchain supports it, or export a reproducible patch/project update for installation through the normal Android update path.
- Preserve a rollback point and report whether build, installation, and runtime verification succeeded.

## Important boundaries: what can and cannot be changed

ForgeAI must distinguish three layers:

1. **ForgeAI-owned rules and filters:** App-authored prompt text, app-level feature gates, and configurable filters can be inspected and changed by the owner-authorized administration system. Do not hide these controls or falsely describe them as immutable.
2. **Model behavior and model policy:** A local model's learned behavior, model license, tokenizer, architecture, and limitations are not rewritten by changing a prompt or app setting. A different model or a separately trained/fine-tuned model may be needed. Do not promise that prompt edits guarantee unrestricted outputs.
3. **Platform/OS/security controls:** Android permissions, package-signing requirements, sandboxing, and operating-system restrictions cannot be silently removed by Natasha. The app must not attempt privilege escalation, root access, or security-control evasion.

If the owner asks to remove a restriction, Natasha should identify which layer owns it. If it is a ForgeAI-owned configurable rule, she can propose and apply the owner's requested change through the authorized update flow. If it belongs to the model, platform, or an external service, explain the actual boundary and offer a legitimate app/model architecture alternative. Never claim a change was made when it was not.

## Update pipeline

Use a staged, auditable process:

1. **Understand:** Translate the request into a concrete change request and identify affected files, settings, features, and risks.
2. **Inspect:** Read the current implementation and dependencies. Do not guess file contents or claim a feature exists without checking.
3. **Plan:** Produce a concise plan and identify whether the change is configuration-only, content-only, or source-code/build-level.
4. **Diff:** Generate a complete patch/diff and explain user-visible effects.
5. **Validate:** Run relevant unit tests, static checks, and build tasks. For code that changes persistence, model loading, permissions, or startup, add targeted tests.
6. **Approve:** Require explicit approval for source-code changes, destructive operations, permission changes, model replacement, or changes that could make the app unlaunchable. Configuration-only changes can be applied immediately when the user clearly requested that exact change.
7. **Backup:** Save the previous configuration/source version and a rollback manifest before applying.
8. **Apply:** Apply only the approved patch to the intended app-owned paths.
9. **Verify:** Re-read changed files, verify checksums/version metadata where appropriate, and report the actual test/build result.
10. **Rollback:** Offer and support restoring the last known-good version if verification fails.

## Security and reliability requirements

- No arbitrary shell execution from natural-language requests.
- No silent modification of the installed APK, Android package manager, signing keys, or OS security configuration.
- No editing outside an explicit app-owned workspace and allowlist.
- No exposing API keys, passwords, private files, or signing credentials to the model or logs.
- Treat model-generated code and downloaded patches as untrusted until inspected and tested.
- Require explicit owner approval for destructive or security-sensitive operations.
- Keep an append-only audit record of requested changes, approved diffs, applied versions, validation results, and rollback events.
- Provide a safe mode that starts the app with the last known-good configuration if a customization breaks startup.
- Updates must not require paid services or silently introduce remote inference dependencies.

## Acceptance criteria

- Natasha can accurately report the current persona/configuration and feature implementation status from app state.
- A direct request to change the persona results in a saved configuration change, followed by read-back verification.
- A request to enable/disable a supported feature updates its actual feature state, not merely its prompt.
- A request to modify source code produces a reviewable diff and test/build result before installation.
- The owner can approve, reject, inspect, and roll back source updates.
- The system distinguishes app-owned filters from model and Android platform constraints.
- No response says "updated", "removed", "installed", or "fixed" unless the corresponding operation actually succeeded and was verified.
- The complete administration workflow can operate locally without paid APIs or cloud coding services.

## Implementation phases

1. **Configuration administration:** Persona editor, settings/feature registry, read-back verification, configuration version history.
2. **Local project inspection:** Read-only access to an app-owned source workspace and feature inventory.
3. **Patch generation:** Structured patch proposals, diff viewer, file allowlist, test runner.
4. **Controlled application:** Approval, backup, apply, build, verification, rollback.
5. **Optional automation:** User-defined narrow auto-approval rules for low-risk configuration changes only. Never enable blanket source-code auto-approval by default.

This is a product specification. It does not claim that source-code self-modification, local code generation, APK rebuilding, or rollback is currently implemented.
