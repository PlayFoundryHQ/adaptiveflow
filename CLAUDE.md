# AdaptiveFlow — working notes & invariants

AdaptiveFlow is an adaptive, icon-first **language flashcard** app for Android
(Kotlin + Jetpack Compose). Core loop: **import** content (paste text / YouTube
link / PDF / txt-csv) → an AI provider turns it into a deck → **study** with
spaced repetition + adaptive difficulty → an **AI tutor** chat → light
**gamification** (XP / streak / "Quest" path).

Full audit + roadmap: **`PLAN.md`**. This file is the short version + the rules
that must not regress.

## Layout

```
data/ai/          AiProvider interface + GeminiProvider + DeepSeekProvider + AiClient
data/settings/    SettingsStore  (EncryptedSharedPreferences for keys, plain prefs otherwise)
data/pdf/         PdfTextExtractor  (PdfBox-Android, Apache-2.0)
data/{database,dao,model,repository}   Room
domain/           SrsScheduler  (pure, unit-tested)
ai/Prompts.kt     all prompt templates, parameterised
ui/               MainActivity.kt  (still one big file — Phase 2 splits it)
                  ui/theme, ui/components, ui/viewmodel/StudyViewModel.kt
AdaptiveFlowApp   custom Application — inits PdfBox
```

## Invariants — do not regress

1. **No API key is bundled in the APK.** The user supplies their own Gemini
   and/or DeepSeek key. Keys live in `EncryptedSharedPreferences` via
   `SettingsStore`, excluded from cloud backup / device transfer
   (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
2. **Import never fabricates content.** If AI parsing fails or no key is set,
   import must fail loudly (`ImportState.Error`) or use the honest offline
   `parseRawTextLocally` ("word: meaning" lists). Never insert canned/unrelated
   cards and report success. (The old "Gemini Nano" fallback was fake — removed.)
3. **The app never claims capabilities it doesn't have** — no "on-device AI",
   no "Gemini Nano", no fake model names in UI copy.
4. **`AppDatabase`: `exportSchema = true`.** Every `version` bump ships a real
   `Migration` in `AppDatabase.MIGRATIONS`. `fallbackToDestructiveMigration` is
   **debug-only** — a schema change must never wipe a real user's decks / SRS
   progress. Committed schemas live in `app/schemas/`.
5. **Talk to `AiProvider`, never a vendor wire type.** New providers implement
   the interface; failures normalise to `AiException.Kind`. No string-matching
   vendor error prose outside the provider classes.
6. **`SrsScheduler` stays pure and tested.** Scheduling math changes come with
   test-vector updates in `SrsSchedulerTest`.
7. **Auth by header, never URL.** Gemini uses `x-goog-api-key`, DeepSeek uses
   `Authorization: Bearer`. Never put a key in a query param (it leaks into
   logged URLs).
8. **Model ids are constants with a "verify before release" TODO**
   (`GeminiProvider.DEFAULT_MODEL`, `DeepSeekProvider.DEFAULT_MODEL`) — check
   them against current provider docs at each release.

## Build & release

- **JDK 17.** `local.properties` with `sdk.dir=…` (git-ignored).
- Bleeding-edge toolchain by choice: AGP 9.1.1, `compileSdk 36.1`. If a runner
  update breaks the build, that's the first suspect.
- `./gradlew lintDebug testDebugUnitTest assembleDebug` is the gate. Keep it green.
- **Debug builds need no keystore** — Android's automatic debug signing. There is
  no custom `debugConfig`; don't add one.
- **Release signing** is wired only when CI injects `KEYSTORE_FILE` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` env vars. `release` has R8 +
  resource shrinking on.
- **One workflow** (`.github/workflows/ci.yml`): PR/push → verify; push to `main`
  (path-filtered) → also build a signed release APK and publish/update the
  GitHub Release. Keeps Actions minutes low — no second workflow, no
  release-please PR churn. Cut a release by bumping `versionName` /
  `versionCode` in `app/build.gradle.kts` in a PR.
- Landing page: `PlayFoundryHQ/adaptiveflow` GitHub Pages (kept — see PLAN.md §6).

## Identity

`namespace` == `applicationId` == Kotlin package == `io.github.playfoundryhq.adaptiveflow`.
Keep them in lockstep. Changing `applicationId` after the first release makes it a
different app with no in-place upgrade.
