# AdaptiveFlow — working notes & invariants

AdaptiveFlow is an adaptive, icon-first **language flashcard** app for Android
(Kotlin + Jetpack Compose, single-Activity). Core loop: **import** content
(paste text / YouTube link / PDF / txt-csv-json) → an AI provider you choose
turns it into a deck → **study** with spaced repetition + adaptive difficulty →
an **AI tutor** chat → light **gamification** (XP / streak / crowns / "Quest"
path).

History + the original audit: **`PLAN.md`**. Current architecture:
**`docs/architecture/overview.md`**. This file is the short version + the rules
that must not regress.

## Layout

```
data/ai/           AiProvider interface + GeminiProvider + DeepSeekProvider + AiClient
data/settings/     SettingsStore (EncryptedSharedPreferences for keys, plain prefs otherwise)
                   LocaleManager  (manual per-app UI language, min SDK 24, no AppCompat)
data/backup/       DeckExporter   (JSON / CSV export)
data/pdf/          PdfTextExtractor (PdfBox-Android, Apache-2.0)
data/{database,dao,model,repository}   Room — schema v3, real migrations, exportSchema=true
domain/            SrsScheduler   (pure, unit-tested)
                   ImportPipeline + ImportParsing (import + staged merge, extracted from the VM)
                   DeckMerge      (pure merge planner, unit-tested)
                   Languages      (curated language list; endonyms; rtl + uiTranslation flags)
di/AppContainer    manual DI — all fields `by lazy`
tts/TtsController  app-scoped; engine bind on a background thread
ai/Prompts.kt      all prompt templates, parameterised on native/target language
ui/                MainActivity (NavHost + bottom bar) · one file per screen:
                   DecksScreen · QuestScreen · ImportScreen · StudyScreen · TutorialScreen
                   TutorSheet · SettingsDialogs · ui/components/ · ui/theme/ · ui/viewmodel/
AdaptiveFlowApp    custom Application — inits PdfBox, holds the AppContainer
res/values/        strings.xml (English, ~270 entries) · res/values-fa/ (Persian, full parity)
```

## Invariants — do not regress

1. **No API key is bundled in the APK.** The user supplies their own Gemini
   and/or DeepSeek key. Keys live in `EncryptedSharedPreferences` via
   `SettingsStore`, excluded from cloud backup / device transfer
   (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
2. **Import never fabricates content.** If AI parsing fails or no key is set,
   import must fail loudly (`ImportState.Error`) or use the honest offline
   `parseRawTextLocally` (`word: meaning` lists). Never insert canned/unrelated
   cards and report success.
3. **The app never claims capabilities it doesn't have** — no "on-device AI",
   no "Gemini Nano", no fake model names in UI copy.
4. **`AppDatabase`: `exportSchema = true`.** Every `version` bump ships a real
   `Migration` in `AppDatabase.MIGRATIONS`. `fallbackToDestructiveMigration` is
   **debug-only**. Committed schemas live in `app/schemas/`. Current version: **3**
   (`MIGRATION_2_3` is a genuine no-op — Int→Long PKs, and SQLite stores every
   INTEGER as 64-bit already, so the generated schema is byte-identical).
5. **Talk to `AiProvider`, never a vendor wire type.** New providers implement
   the interface; failures normalise to `AiException.Kind`. No string-matching
   vendor error prose outside the provider classes.
6. **`SrsScheduler` stays pure and tested.** Scheduling-math changes come with
   test-vector updates in `SrsSchedulerTest`. The in-session queue re-order is a
   separate, deliberate layer on top — don't merge the two blindly.
7. **Auth by header, never URL.** Gemini uses `x-goog-api-key`, DeepSeek uses
   `Authorization: Bearer`. Never a key in a query param (it leaks into logged
   URLs).
8. **Model ids are constants with a "verify before release" TODO**
   (`GeminiProvider.DEFAULT_MODEL`, `DeepSeekProvider.DEFAULT_MODEL`) — check
   them against current provider docs at each release.
9. **All user-facing copy goes through `strings.xml`.** No hardcoded UI strings.
   Interpolations use **positional** format args (`%1$s`, `%2$d`, `%%` for a
   literal percent) so lint's `StringFormat*` checks catch an arg mismatch at
   **build** time rather than crashing at runtime. Every `values/` string has a
   `values-fa/` counterpart with identical arg positions. **Not** localised:
   `Prompts.kt` (English instructions to the model), the tutor quick-action
   prompt strings, the copyable "external AI" prompt template, and
   `DiagnosticLogsDialog` (developer tooling).
10. **`domain/` and the pure planners stay unit-testable.** `ImportParsing`,
    `DeckMerge`, `SrsScheduler`, `Languages` have no Android/Compose deps.

## Localisation

- **UI languages:** English (`values/`) + Persian (`values-fa/`). The "my
  language" picker in the goal dialog is the single control — picking a language
  whose `Languages` entry has `uiTranslation = true` calls
  `LocaleManager.apply(activity, tag)`, which persists the BCP-47 tag and
  `recreate()`s. `MainActivity.attachBaseContext` wraps the base context with an
  overridden `Configuration` locale + layout direction.
- **RTL:** `android:supportsRtl="true"`; use `start`/`end`, never `left`/`right`;
  directional icons must be `Icons.AutoMirrored.*`. The Low→Med→High rating row
  is deliberately pinned LTR (`LocalLayoutDirection`) so its red→green order
  never inverts.
- **Adding a locale:** drop `res/values-<code>/strings.xml` (full parity), set
  `uiTranslation = true` on that `Languages` entry. No AppCompat, no
  `localeConfig` (Android-13 system integration is deferred — see PLAN.md §11).

## Build & release

- **JDK 17.** `local.properties` with `sdk.dir=…` (git-ignored).
- Bleeding-edge toolchain by choice: AGP 9.1.1, `compileSdk 36.1`. If a runner
  update breaks the build, that's the first suspect.
- `./gradlew lintDebug testDebugUnitTest assembleDebug` is the gate. Keep it green.
  Known-flaky: a KSP-daemon race (`ApplicationManager.getApplication() is null`)
  that self-recovers on Gradle's retry.
- **Debug builds need no keystore** (Android auto debug signing). No custom
  `debugConfig` — don't add one.
- **Release signing** is wired only when CI injects `KEYSTORE_FILE` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`. `release` has R8 + resource
  shrinking on. `lint { checkReleaseBuilds = false }` (CI already runs `lintDebug`).
- **Robolectric screenshot tests** pin `@Config(sdk = [34])` — Robolectric 4.16
  has no SDK 36 image. Plain `testDebugUnitTest` renders them (crash smoke-test)
  but writes/diffs no PNG without a `record`/`verify` flag.
- **One workflow** (`.github/workflows/ci.yml`): PR/push → verify; push to `main`
  (path-filtered, `docs/` excluded) → also build + publish the signed release
  APK. Cut a release by bumping `versionName` / `versionCode` in a PR.
- Landing page: `docs/index.html`, self-contained, served via GitHub Pages
  (Settings → Pages → branch `main` `/docs`).

## Identity

`namespace` == `applicationId` == Kotlin package == `io.github.playfoundryhq.adaptiveflow`.
Keep them in lockstep. Changing `applicationId` after the first release makes it a
different app with no in-place upgrade.
