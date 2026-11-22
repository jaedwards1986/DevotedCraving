# ForgeAI delivery roadmap

This roadmap tracks the eight major delivery milestones. A milestone is only marked complete after its acceptance checks pass; a code commit by itself is not proof of completion.

## Batch 1 — product foundation through maintenance

### 1. Foundation and app identity — in progress
- [x] ForgeAI product branding in the Android project and launcher label.
- [x] Preserve the existing Android application ID and Room database identity to avoid an unplanned data migration.
- [x] Verify a clean debug and release build in GitHub Actions (run 44983791650).

### 2. Natasha identity and memory — in progress
- [x] Keep Natasha's immutable adult character foundation separate from model selection and chat history.
- [x] Keep stored memories as context rather than character instructions.
- [x] Add prompt regression tests for supported prompt templates and hostile profile overrides.
- [ ] Verify behavior on-device with at least two supported local model families.

### 3. Chat experience — in progress
- [x] Chat history, multiple conversations, streaming, cancellation, and bounded text/source attachments.
- [x] Neutral dark ForgeAI presentation; Natasha's personality remains in the chat.
- [ ] Complete interaction, accessibility, and long-conversation testing.

### 4. Local model management — in progress
- [x] Import GGUF models into app-private storage.
- [x] Record SHA-256 metadata and support model selection.
- [x] Keep model switching separate from Natasha's identity and project data.
- [ ] Validate model compatibility, memory use, and inference stability on the target phone.

### 5. Project workspace — in progress
- [x] Store project descriptions and private project files.
- [x] Create, read, edit, delete, move, and rename workspace files.
- [x] ZIP export/import with path validation, size limits, staging, and rollback.
- [x] Keep a bounded project activity journal.
- [ ] Test larger projects and interrupted archive operations.

### 6. Project-aware AI development — in progress
- [x] Add bounded source-file context to prompts for the selected project.
- [x] Exclude hidden/generated folders and redact common credential/token patterns from prompt context.
- [x] Parse structured multi-file proposals from the local model.
- [x] Validate proposals and require preview plus explicit user approval before applying changes.
- [ ] Test complete model-to-preview-to-apply workflows with real GGUF models.
- [ ] Add reliable multi-step build/test feedback; no shell or Gradle execution is enabled yet.

### 7. Maintenance and recovery — in progress
- [x] Schedule daily WorkManager health checks.
- [x] Check Room database integrity and report low storage.
- [x] Remove only abandoned temporary project-import staging folders older than 48 hours.
- [x] Add a manual maintenance trigger and persist the last report.
- [ ] Verify worker behavior across reboot, app upgrade, and low-storage conditions.
- [ ] Design and test non-destructive database backup/restore before adding migrations.

## Batch 2 — release

### 8. Release and Pixel verification — not started
- [x] Obtain a successful CI run for unit tests and debug/release APK assembly (run 44983791650).
- [x] Fix the compile/test failures observed in earlier runs; run 44983791650 passed tests and both APK assembly tasks.
- [ ] Download and independently verify APK artifact contents and checksums.
- [ ] Install and exercise the app on a Google Pixel 9 Pro XL.
- [ ] Measure model loading, memory pressure, thermal behavior, and sustained inference.
- [ ] Produce a signed release build and document installation/update steps.

## Current verification note

GitHub Actions run 44983791650 completed successfully on commit 23cbac1389d2596f4b309c02b6935f0e9900e9e9. A later cancellation/archive recovery fix and Room index alignment are being revalidated on subsequent commits. The unit-test task, native C++ compilation, debug APK assembly, release APK assembly, and both artifact uploads succeeded. Third-party KleidiAI code emitted floating-point promotion warnings; they did not fail the build. The APKs have not yet been installed or exercised on a Pixel 9 Pro XL, and the release APK is not signed for distribution.
