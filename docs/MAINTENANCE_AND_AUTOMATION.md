# ForgeAI maintenance and update discovery

## What is implemented

- A unique daily WorkManager job runs a local SQLite integrity check and validates the stored endpoint/model/token settings.
- A unique 30-day WorkManager job checks the public ForgeAI GitHub Releases endpoint.
- The release worker records the latest tag, release URL, publication date, and whether the tag differs from the installed app version.
- Neither worker uploads conversation text, prompts, memories, API keys, or diagnostics.
- Release discovery is advisory only. It does not install APKs, replace model files, or modify personality settings without an explicit user action.

## Scheduling limitations

Android WorkManager is persistent but intentionally inexact. Doze mode, battery optimization, connectivity constraints, and device shutdown can defer a scheduled run. Daily and monthly therefore mean recurring targets, not exact clock-time guarantees. See the Android WorkManager documentation:
https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

## Current scope and remaining integration

The maintenance results are stored in the private app preferences file `forgeai_maintenance`. A future maintenance screen should render these values and offer a manual "check now" action. Continuous server-side uptime monitoring is intentionally not claimed: the app has no always-on backend, and local background execution cannot monitor a device while Android has stopped the process. Crash-free operation must be established through CI, device acceptance tests, and user-authorized local diagnostics.

## Free-operation rule

The feature-discovery request is an unauthenticated public GitHub API GET. No paid inference endpoint is used by either maintenance worker. This does not yet convert the existing chat transport to local inference; that remains a separate runtime integration and acceptance-test requirement.
