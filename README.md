# NEXORA

Fresh Android rebuild on `main`. NEXORA is a free, offline-first Android app combining a fictional adult companion experience with a source-verified public Natasha Nice archive.

The companion is fictional and does not represent Natasha Nice or claim access to her private life. The archive contains only authentic, public, source-linked records. The app does not fabricate archive media or bypass access controls.

## Build
Run `gradle assembleDebug`. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and publishes the debug APK artifact.

This initial commit establishes the clean Android foundation. Local llama.cpp inference, GGUF model management, persistent memory, secure PIN, and archive indexing are subsequent implementation milestones; they are not yet complete.

No paid AI APIs, subscriptions, ads, mandatory accounts, remote chat inference, or silent updates.
