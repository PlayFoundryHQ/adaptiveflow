# AdaptiveFlow — End‑to‑End Audit & Remediation Plan

> Status: audit 2026‑09‑09 · **Phase 0 + Phase‑1 essentials — MERGED (PR #1), v0.1.0 released**. Was on branch
> `feat/stabilize-and-multi-provider-ai` (see §10 progress log) · Companion
> project: **Slim** (`../Slim`, `PlayFoundryHQ/Slim`)
>
> This document is the single source of truth for getting AdaptiveFlow from
> "AI‑Studio export that has never had a green build" to "a maintainable,
> releasable Android app". Every finding has a severity, a file reference, and a fix.
>
> **User decisions (2026‑09‑09):** (1) support ≥2 AI providers — Gemini **and**
> DeepSeek; (2) remove the fake "Gemini Nano"; (3) move repo to
> `PlayFoundryHQ/adaptiveflow`, package → `io.github.playfoundryhq.adaptiveflow`;
> (4) stay on the bleeding‑edge toolchain (AGP 9.1 / SDK 36.1); (5) release
> cadence = model's call → simple "push‑to‑main builds a signed release"
> (no release‑please); (6) keep the GitHub Pages landing page, update as we go.

---

## 0. TL;DR — the verdict

AdaptiveFlow is a **genuinely interesting product** (adaptive, icon‑first language
flashcards with AI import + AI tutor + spaced repetition + gamification) buried
under a **raw AI‑Studio code dump** that has **never built in CI** and carries
several correctness, licensing, and security problems.

| Dimension | State | One‑line |
|---|---|---|
| **Builds from a clean clone?** | ❌ **No** | `debugConfig` signing config points at `debug.keystore`, which is `.gitignore`d and absent. `./gradlew assembleDebug` fails at `validateSigningDebug`. |
| **CI** | ❌ **0 green runs, ever** | Both workflows fail; `Release` fails in ~6s (missing `release-please-config.json`). Workflows are copy‑pasted from another project ("chistanland"). |
| **Code compiles?** | ✅ Yes (once signing is fixed) | Debug APK builds in ~45s, 24.7 MB, **10 dex files**. Only deprecation warnings. |
| **Architecture** | ❌ Severe | `MainActivity.kt` = **5,298 lines**, `StudyViewModel.kt` = **1,961 lines**. No navigation lib, no DI, no module boundaries, no state hoisting discipline. |
| **AI layer** | ⚠️ Confused + partly fake | API key baked into APK **and** sent as `?key=` URL param. Preferred model `gemini-3.5-flash` (does not exist → every call wastes a round trip). "Gemini Nano on‑device AI" is **fabricated** — it is hardcoded phrasebook decks + regex text splitting + canned chat strings, presented to the user as a real local model. |
| **Data integrity** | ❌ Real bug | On AI failure, PDF/text import **silently inserts hardcoded tourist‑phrase decks** unrelated to the user's source document, labelled as a successful import. `fallbackToDestructiveMigration()` wipes all user data on any schema change. |
| **Licensing** | ❌ Legal risk | `com.itextpdf:itextg:5.5.10` (iText 5) is **AGPLv3**. Shipping a closed‑source APK with it is a license violation. Also 2015‑era and unmaintained. |
| **Security** | ⚠️ | Gemini key in `BuildConfig` (extractable from APK) + logged via `?key=` in URLs if the OkHttp logging interceptor is ever enabled. User's own key stored in plain `SharedPreferences`. |
| **Firebase** | 🗑️ Dead weight | `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-bom`, `google-services` plugin — **zero references in code**. Pure bloat + build complexity. |
| **Theming / i18n** | ❌ | **505 hardcoded `Color(0x…)`**, **0 `stringResource()`**, `strings.xml` has **1 entry**. Dark mode disabled (`darkTheme = false`). Package still `com.example`, app still `Theme.MyApplication`, project still `"My Application"`. |
| **State persistence** | ❌ | **0 `rememberSaveable`** anywhere. Rotate / return from background and the user is bounced to the Decks tab, forms clear, dialogs close, study position resets. |
| **Tests** | ❌ Theatre | 4 test files, all boilerplate (`assertEquals(4, 2 + 2)`). CI's `testDebugUnitTest` passes trivially and verifies nothing. |
| **Docs** | ❌ None | No `CLAUDE.md`, no `docs/`, README is the stock AI‑Studio blurb. |

**Recommended posture:** this is a **stabilise‑then‑refactor**, not a rewrite. The
product logic (SRS, import chunking, tutor prompts) is worth keeping; it needs to
be pulled out of the two god‑files, de‑faked, secured, and put behind a green
pipeline. Estimated: **Phase 0 (unblock) ≈ half a day**, **Phases 1–2 (foundation
+ architecture) ≈ 1–2 weeks**, the rest incremental.

---

## 1. What AdaptiveFlow is (reconstructed from code + `metadata.json`)

> `metadata.json`: *"An adaptive, icon‑driven language flashcard application
> utilizing Zero‑Language UI philosophy and real‑time AI Tutor."*

### Core loop

1. **Import** (`ImportTab`, `StudyViewModel.importDeckFromRawText`)
   Paste text / a YouTube URL / attach a PDF or `.txt`/`.csv`. Gemini turns it
   into a structured deck (`ParsedDeck` → `Deck` + `List<Flashcard>`). PDFs are
   text‑extracted page‑by‑page (iText), chunked, parsed chunk‑by‑chunk with
   recursive subdivision on "payload too large", and merged. A "density" knob
   (Focused / Balanced / Exhaustive) tunes how many cards per chunk.
2. **Study** (`StudySessionScreen`, `InteractiveFlashcard`)
   Flip cards; rate confidence LOW / MEDIUM / HIGH. An SM‑2‑ish scheduler
   (`recordCardConfidence`) updates `easeFactor` / `interval` / `repetitions` /
   `nextReview` and re‑orders the in‑session queue. A "Play Mode" flips
   struggled cards to multiple‑choice quiz mode.
3. **AI Tutor** (`AiTutorBottomSheet`, `StudyViewModel.sendTutorMessage`)
   Chat sheet during study. Sends deck + card + session‑struggle context; the
   tutor is told to mirror the user's language and get gentler as the struggle
   streak rises. History persisted in `chat_logs`.
4. **Gamification** (`PathTab` a.k.a. "Quest", `recordStudyActivity`)
   XP (+15 correct / +5 wrong), day streak, a Leitner‑box progress row, a
   "Master Vocabulary Pool" shared deck that is auto‑created and cannot be
   deleted.
5. **TTS** — native `TextToSpeech`, auto‑plays the card front on advance, with
   language‑name → `Locale` mapping and text cleanup for non‑Latin scripts.

### Design language

"Zero‑Language UI" = icon‑first, minimal chrome, one soft indigo→lavender
gradient background. (Note: the bottom nav currently contradicts this — every
item has a text label: *Decks / Quest / Import / Guide*.)

### Screens (all in `MainActivity.kt`)

`DecksTab` · `PathTab` · `ImportTab` · `TutorialTab` · `StudySessionScreen` ·
`AiTutorBottomSheet` · `ApiKeySettingsDialog` · `LearningGoalSettingsDialog` ·
`DiagnosticLogsDialog` (in `ui/components/`).

---

## 2. How to build it *right now* (Phase 0 unblock — do this first)

The code is fine; the packaging config is broken. Two problems:

### 2.1 SDK location
No `local.properties`. Create it (git‑ignored already):
```
sdk.dir=/absolute/path/to/Android/Sdk
```
or export `ANDROID_HOME`. SDK 36.1 + build‑tools 36.0.0 are required (installed on this machine).

### 2.2 The broken `debugConfig` signing config — **root cause of every red CI run**

`app/build.gradle.kts`:
```kotlin
signingConfigs {
  create("debugConfig") {
    storeFile = file("${rootDir}/debug.keystore")   // ← .gitignore'd, not in repo
    ...
  }
}
buildTypes {
  debug { signingConfig = signingConfigs.getByName("debugConfig") }   // ← forces the missing keystore
}
```
`debug.keystore` is listed in `.gitignore` and is not committed, so **a clean
clone cannot build `assembleDebug`** — it dies at `:app:validateSigningDebug`
with *"Keystore file … not found"*. This is exactly why the README tells you to
delete a line by hand.

**Fix (adopt Slim's pattern):** delete the entire custom `debugConfig`. Android's
default debug signing is automatic and needs no config. For `release`, keep a
single `release` signing config that is **only** wired up when CI injects the
keystore via env vars (see §6.1).

```kotlin
signingConfigs {
  create("release") {
    if (System.getenv("KEYSTORE_FILE") != null) {
      storeFile = file(System.getenv("KEYSTORE_FILE"))
      storePassword = System.getenv("KEYSTORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }
}
buildTypes {
  debug { /* nothing — default debug signing */ }
  release {
    isMinifyEnabled = true            // see §4
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    if (System.getenv("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
  }
}
```

**Verification for Phase 0:** `./gradlew assembleDebug` produces
`app/build/outputs/apk/debug/app-debug.apk` from a clean clone with only a
`local.properties`. (Confirmed working once `debugConfig` is removed — build
succeeds in ~45s.)

---

## 3. Gap Analysis (severity‑ranked)

Severity: **P0** = blocks build/release or ships broken/illegal · **P1** = serious
correctness / security / architecture debt · **P2** = quality, maintainability,
UX · **P3** = polish.

### 3.1 Build, packaging & release

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| B1 | **P0** | Clean clone cannot build — `debugConfig` → missing `debug.keystore` | `app/build.gradle.kts:36-40, 55` | Delete `debugConfig`; use default debug signing (§2.2) |
| B2 | **P0** | CI has **never** produced a green run (both workflows fail on every push) | GitHub Actions history | Rewrite both workflows (§6.1) |
| B3 | **P0** | `release.yml` references `release-please-config.json` + `.release-please-manifest.json` — **neither exists** → Release job fails in ~6s | `.github/workflows/release.yml` | Either add the two config files, or drop release‑please for Slim's simpler "signed APK on push to main" (§6.1) |
| B4 | **P0** | `build.yml` copies `scripts/landing-page.html` + `scripts/*.png` into `public/` for GitHub Pages — **`scripts/` does not exist** → deploys an empty/broken page | `.github/workflows/build.yml` | Remove the Pages steps, or add a real `scripts/landing-page.html` |
| B5 | **P0** | Workflows and release asset name (`chistanland-latest.apk`) are lifted verbatim from a different project | both workflows | Rename everything to `adaptiveflow`; strip the copied comments |
| B6 | **P1** | `com.itextpdf:itextg:5.5.10` is **AGPLv3** — shipping a proprietary APK with it is a license violation | `gradle/libs.versions.toml`, `app/build.gradle.kts:114` | Replace with **Apache‑2.0** PdfBox‑Android (`com.tom-roush:pdfbox-android`) — note `libs.versions.toml` already declares `pdfboxAndroid = "2.0.27.0"` but the lib entry mispoints it back to `itextg`. Or move PDF text extraction server‑side. |
| B7 | **P1** | Release build ships **unshrunk** — `isMinifyEnabled = false`, 10 dex files, ~25 MB debug APK; release would be similar | `app/build.gradle.kts:47` | Enable R8 + resource shrinking for release; add keep rules for Moshi/Room/Retrofit (port Slim's `proguard-rules.pro` approach) |
| B8 | **P1** | `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-bom`, `google-services` plugin — **zero code references** | `app/build.gradle.kts`, `build.gradle.kts` | Delete all of it. Removes the `google-services` plugin, `googleServices { }` block, `googleServices.missing.passthrough`, and a large chunk of the dex. |
| B9 | **P1** | Bleeding‑edge toolchain with no lockfile discipline: AGP **9.1.1**, `compileSdk = release(36) { minorApiLevel = 1 }`, Robolectric `sdk = [36]` (Robolectric 4.16 SDK‑36 support is new). One of these will break on a runner update. | `gradle/libs.versions.toml` | Pin to a stable published AGP (8.7.x / 8.9.x) + `compileSdk = 35` unless 36 APIs are actually needed; keep `targetSdk` deliberate |
| B10 | P2 | `namespace = "com.example"`, `applicationId = "com.aistudio.adaptiveflow.zqyhmp"` (AI‑Studio random suffix), `rootProject.name = "My Application"`, `Theme.MyApplication`, `MyApplicationTheme` | multiple | Pick a real identity, e.g. `io.github.opscalehub.adaptiveflow` (reverse‑DNS of the GH Pages site; F‑Droid convention). Rename in lockstep: namespace / applicationId / Kotlin package / theme / project name. *(Same lesson Slim learned — see §6.7.)* |
| B11 | P2 | `sourceCompatibility = VERSION_11` but CI + local use JDK 17; `foojay-resolver` toolchain plugin present but no `kotlin { jvmToolchain(17) }` | `app/build.gradle.kts:52-53` | Standardise on JVM 17 (`compileOptions` + `kotlinOptions.jvmTarget` or `jvmToolchain`) |
| B12 | P2 | No `.editorconfig`, no ktlint/spotless, no detekt; 2‑space indent (AI‑Studio) vs Android's 4‑space | repo root | Add spotless/ktlint + `.editorconfig`; reformat once |
| B13 | P3 | `README.md` is the stock "Run and deploy your AI Studio app" banner | `README.md` | Rewrite for the real project |
| B14 | P3 | `AndroidManifest.xml` still uses `@xml/backup_rules` / `@xml/data_extraction_rules` sample stubs (all commented out) → user data (incl. **the API key in SharedPreferences**) is cloud‑backed up by default | `AndroidManifest.xml`, `res/xml/*` | Decide backup policy explicitly; exclude the prefs file that holds the key, or move the key to `EncryptedSharedPreferences` and out of backup |

### 3.2 Architecture

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| A1 | **P1** | `MainActivity.kt` is **5,298 lines** — 20 top‑level composables, `ImportTab` ≈ 1,200 lines, `StudySessionScreen` ≈ 1,000, `PathTab` ≈ 760, all inline | `MainActivity.kt` | Split into `ui/<feature>/` files; one screen per file; extract reusable pieces to `ui/components/` |
| A2 | **P1** | `StudyViewModel.kt` is **1,961 lines** — DB flows + SRS + gamification + PDF chunking + JSON repair + retry/backoff + prompt templates + TTS + API‑key management, all in one `AndroidViewModel` | `StudyViewModel.kt` | Decompose: `ImportRepository` / `ImportUseCase`, `SrsScheduler`, `TutorRepository`, `SettingsRepository`, `TtsController`, `GamificationRepository`. VM becomes thin state holders (one per screen). |
| A3 | **P1** | No DI — `AppDatabase.getDatabase(context)` manual singleton, VM `new`s its own `StudyRepository`, `RetrofitClient` is a global `object`, `DiagnosticLogger` is a process‑global `object` holding a `StateFlow<List<LogEntry>>` | everywhere | Add Hilt (or a small manual `AppContainer`). Makes everything testable and removes the globals. |
| A4 | **P1** | No navigation — routing is `when (activeTab)` + `if (currentDeck != null)` in `MainScreen`; no back stack, no deep links, no saved nav state | `MainActivity.kt:124-165` | Adopt `androidx.navigation.compose` (already a declared, commented‑out dependency) or Compose‑nav 2 / a typed nav library |
| A5 | **P1** | **0 `rememberSaveable`** in the whole app. `activeTab`, every dialog‑open flag, every form field, `currentCardIndex` view‑side state, `isSessionStarted` — all `remember` only → lost on rotation / process death | whole `MainActivity.kt` | `rememberSaveable` for transient UI state; screen state that must survive process death goes into `SavedStateHandle` in the VM |
| A6 | P2 | `allDecks` is declared `StateFlow`, initialised as `MutableStateFlow(emptyList())`, then written via `(allDecks as MutableStateFlow).value = …` inside a `collectLatest` | `StudyViewModel.kt:55, 669, 682` | `repository.allDecksWithCardsFlow.stateIn(viewModelScope, WhileSubscribed(5000), emptyList())` |
| A7 | P2 | `withContext(Dispatchers.Main) { _state.value = … }` sprinkled throughout — `MutableStateFlow.value` is already thread‑safe; these hops add latency and noise | `StudyViewModel.kt` (dozens) | Delete the `withContext(Main)` wrappers around pure state writes |
| A8 | P2 | Composables take the concrete `StudyViewModel` as a parameter and read `viewModel.getEffectiveApiKey()` etc. directly in composition | `MainActivity.kt` throughout | Hoist state: pass immutable UI‑state + lambdas; keep VM at the screen root only |
| A9 | P2 | `DiagnosticLogger.getLogcatLogs()` shells out to `Runtime.exec("logcat")` | `DiagnosticLogger.kt:88` | Fine for a debug drawer; gate it to `BuildConfig.DEBUG` and never in release |
| A10 | P3 | `ParsedCard` / `ParsedDeck` (API DTOs) live in the VM file; `Content`/`Part`/`Blob` (wire types) live in `GeminiApi.kt` and leak into the VM | `StudyViewModel.kt:35-48` | Move DTOs to `data/api/dto/`, add domain models, map at the repository boundary |

### 3.3 AI layer — correctness, honesty, security

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| AI1 | **P1** | **Fabricated "Gemini Nano" on‑device AI.** With no API key: import runs `generateThematicCardsLocally` → returns **hardcoded French/Spanish/Japanese/German/Italian/Swedish phrasebook decks**; chat returns canned template strings prefixed `"📱 [Gemini Nano On‑Device AI]"`; the settings dialog tells the user *"Running entirely on‑device, offline and secure… Gemini Nano Fallback"*. There is no on‑device model. | `StudyViewModel.kt:923-1113, 534-561`; `MainActivity.kt` ApiKeySettingsDialog | Remove the fake. If offline handling is wanted, make it honestly: "No API key — AI features disabled" + a real offline path (plain list parsing only). If real on‑device is wanted later, use ML Kit GenAI / AICore (`gemini-nano`) behind a capability check. |
| AI2 | **P1** | On **AI failure or malformed JSON**, PDF/text import falls through to `generateThematicCardsLocally` and **inserts canned tourist‑phrase cards** as if they were parsed from the user's document, with `ImportState.Success`. The user believes their PDF imported. | `StudyViewModel.kt:1155-1173` (Nano path) & the fabricated‑card generators | Import must **fail loudly** when it cannot extract real content. Never substitute unrelated content. |
| AI3 | **P1** | `preferredModel = "gemini-3.5-flash"` — not a real model id. Every single request fails the preferred call, waits, then falls back to `gemini-2.5-flash`. With a 120 s read timeout the failure path can cost minutes. | `GeminiApi.kt:85` | Set model ids from the **current** Gemini API docs (verify — do not trust this doc's memory). Make model configurable, not a magic string. Drop the fake "preferred". |
| AI4 | **P1** | API key sent as `?key=<KEY>` query param. If `logging-interceptor` (a declared dependency) is ever attached at `BODY`/`BASIC` level, keys land in logcat and crash reports. | `GeminiApi.kt:59`; `logging-interceptor` in deps | Send the key as the `x-goog-api-key` **header**; never log full URLs; redact in any interceptor |
| AI5 | **P1** | Gemini API key is compiled into `BuildConfig.GEMINI_API_KEY` (secrets‑gradle‑plugin) → trivially extractable from the APK. Anyone can drain the project quota / rack up billing. | `app/build.gradle.kts` secrets block; `StudyViewModel.getEffectiveApiKey()` | For a real release: proxy Gemini through a tiny backend (Cloud Function / Firebase AI Logic — the thing `metadata.json`'s `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` implies) so the key never ships. If staying key‑in‑app for now: **only** the user's own pasted key, stored in `EncryptedSharedPreferences`, no bundled project key. |
| AI6 | P2 | Error classification is ~120 lines of `throwable.message.contains("429")`, `.contains("quota")`, `.contains("experiencing high demand")` … across `isFatalApiError` / `isRateLimitError` / `isTransientServerError` / `isPayloadTooLargeError` | `StudyViewModel.kt:271-376` | Parse the structured Gemini error JSON (`error.code`, `error.status`, `error.details[].retryInfo.retryDelay`). Keep string matching only as a last resort. *(cf. the `verify‑threshold‑regex‑boundary‑logic` skill — test these against real error bodies.)* |
| AI7 | P2 | Prompt templates (300+ line multiline strings) are inlined in VM methods; identical system prompt duplicated between the chunked and non‑chunked import paths | `StudyViewModel.kt:415-445, 366-415` | Move to a `prompts/` package (string resources or constants); one template, parameterised |
| AI8 | P2 | `robustParseJsonDeck` third fallback is a hand‑written regex over `{"front":"…","back":"…"}` — breaks on any escaped quote or nested brace | `StudyViewModel.kt:230` | Rely on `responseMimeType = "application/json"` + a lenient JSON reader; drop the regex tier |
| AI9 | P2 | OkHttp timeouts are **120 s** on connect **and** read **and** write | `GeminiApi.kt:73-76` | connect ≈ 15 s, read ≈ 60 s, call‑timeout as an overall cap; surface a cancel button in the UI |
| AI10 | P2 | Tutor "history" is flattened to `"USER: …"` / `"AI: …"` `Part`s inside a single `Content` list with no `role` field → Gemini does not see it as a real multi‑turn conversation | `StudyViewModel.kt:594-603` | Use proper `contents` with `role: "user" / "model"` |
| AI11 | P3 | `RetrofitClient` is a global `object` with a `by lazy` service and a hardcoded base URL — cannot be swapped for tests / staging | `GeminiApi.kt:63` | Inject the `Retrofit`/service; base URL from `BuildConfig` |

### 3.4 Data layer & correctness

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| D1 | **P1** | `AppDatabase` uses `fallbackToDestructiveMigration()` — **any** schema change wipes every deck, card, SRS state and chat log the user has. `exportSchema = false` so there is no schema history to migrate from. | `AppDatabase.kt:27, 12` | `exportSchema = true`, commit `app/schemas/`, write real `Migration` objects. Destructive fallback only in `debug`. |
| D2 | **P1** | "Master Vocabulary Pool" is auto‑inserted from **inside a `collectLatest`** on the deck flow, guarded only by `decks.any { it.name.contains("Master Vocabulary Pool") }`. Two emissions before the insert commits → **duplicate pools**. Also fires an insert on every app start until the flow catches up. | `StudyViewModel.kt:680-696` | Seed once in a transaction on first run (a `has_seeded` flag already exists but is unused: `hasSeeded()` at `:616`). Use a deterministic PK / unique index on deck name for the pool. |
| D3 | P1 | `Flashcard` / `Deck` / `ChatLog` PKs are `Int` with `autoGenerate = true`; `insertFlashcards` uses `OnConflictStrategy.REPLACE`. Room autoGenerate expects `Long`; REPLACE on an autoGenerate PK deletes+reinserts and cascades `ForeignKey.CASCADE` on `chat_logs` | `Entities.kt`, `StudyDao.kt` | PKs → `Long`; use `IGNORE` or explicit upserts; add a real natural‑key unique index for dedupe |
| D4 | P1 | Fragmented persistence: decks/cards/chat in **Room**; XP, streak, `last_study_date`, `has_seeded`, `gamified_xp`, learning goal, API key all in **`SharedPreferences`** (17 call sites), TTS rate / autoplay / play‑mode only in memory (lost on process death) | `StudyViewModel.kt` | One story: user settings → `DataStore`; gamification/progress → Room (queryable, backupable, migratable); ephemeral session state → `SavedStateHandle` |
| D5 | P1 | `saveOrMergeCards` "smart dedup + enrichment" does fuzzy `lowercase().replace(punct).replace(\s+)` matching and **string‑concatenates alt translations into the `notes` field** (`"\n• Alt translation: …"`). Repeated imports of the same source keep appending. No way to undo. | `StudyViewModel.kt:998-1080` | Make merge explicit and reversible; store alternates as structured data, not by mutating a free‑text field; show the user a diff before applying |
| D6 | P2 | SRS scheduler is a bespoke SM‑2 variant with hand‑tuned constants and a *second*, separate "re‑insert into session queue at position 2 / middle / end" heuristic that runs alongside the `nextReview` math — two schedulers fighting | `StudyViewModel.kt:766-861` | Pick one model. Extract a pure `SrsScheduler` with unit tests over known inputs. |
| D7 | P2 | `nextReview = now + newInterval * 24*60*60*1000L` — `newInterval` is `Int`, the multiply is `Int * Long` only because of the `L`; large intervals are fine but the "due today" logic elsewhere never actually filters by `nextReview` (cards are just sorted by it) | `StudyViewModel.kt:815` | Decide whether reviews are due‑gated or just ordered; implement consistently |
| D8 | P2 | `getMultipleChoiceOptions` pads distractors from a hardcoded English list (`"Apple", "Book", "Sun"…`) — wrong for non‑English decks | `StudyViewModel.kt:? (getMultipleChoiceOptions)` | Draw distractors only from the same deck; if too few cards, disable quiz mode |
| D9 | P2 | `copyUriToCacheFile` writes `temp_import_<ts>.pdf` to `cacheDir`, deletes in a `finally` — but the recursive PDF path holds `PdfReader` open across suspend points and the 150 000‑char text cap is applied with a raw `CharArray(150000)` read that can split a multi‑byte char | `StudyViewModel.kt:1157-1259` | Stream + bound by bytes with a proper reader; close `PdfReader` in `use {}`; process off the main dispatcher (already IO — good) |
| D10 | P3 | `DiagnosticLogger` keeps the last 200 log entries in a `MutableStateFlow` for the lifetime of the process; `synchronized(this)` + full list copy on every log line | `DiagnosticLogger.kt:44-58` | Ring buffer; cap lower; debug‑only |

### 3.5 UI / UX

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| U1 | P1 | **505 hardcoded `Color(0x…)`**, **525 `.dp` literals**, **0 `stringResource()`**, `strings.xml` = 1 entry. No design tokens, no theming, no i18n path. | whole `MainActivity.kt` | Build a real `AdaptiveFlowTheme` (color scheme, typography, spacing tokens); move copy to `strings.xml`; then delete the inline literals feature by feature |
| U2 | P1 | Dark mode disabled: `MyApplicationTheme(darkTheme = false, dynamicColor = false)` + a hardcoded light gradient in `setContent`. `Theme.kt` supports dark + dynamic but is bypassed. | `MainActivity.kt:94-119` | Wire the theme to `isSystemInDarkTheme()`; make the gradient theme‑aware or drop it |
| U3 | P1 | "Zero‑Language UI" but the bottom nav has 4 text labels (*Decks / Quest / Import / Guide*) and screens are text‑heavy | `ZeroLanguageNavigationBar` | Decide: commit to icon‑only nav with long‑press tooltips, or drop the "zero‑language" framing from the marketing |
| U4 | P1 | No state restoration (see A5) → rotate during study = back to card 1 / Decks tab; rotate mid‑import = form cleared | everywhere | `rememberSaveable` + `SavedStateHandle` |
| U5 | P2 | `BackHandler` only exists in `StudySessionScreen`. On other tabs, system back exits the app even when a dialog is open or you're 3 tabs deep | `MainActivity.kt:2329` | Proper nav back stack (A4) handles this |
| U6 | P2 | Session‑complete is `flippedCardIds.size >= cards.size` — flipping every card once (without answering) ends the session; and `flippedCardIds` is a `Set<Int>` on `Flashcard.id` so it never resets between decks with overlapping ids | `MainActivity.kt:2391`; `StudyViewModel.kt:89` | Track answered‑this‑session explicitly, keyed to the session not the card id |
| U7 | P2 | File picker uses `GetContent()` with `launch("*/*")` — accepts any file; no MIME filter; relies on `getType()` string matching downstream | `MainActivity.kt:1080, 1690` | `OpenDocument()` with `arrayOf("application/pdf","text/plain","text/csv","text/comma-separated-values")` |
| U8 | P3 | `Toast` used for important outcomes ("API Key saved successfully!"); persistent success banner added later but Toast still elsewhere | `MainActivity.kt` ApiKey dialog | Snackbar / inline confirmation consistently |
| U9 | P3 | 15+ deprecated `Icons.Filled.*` (use `Icons.AutoMirrored.Filled.*`), deprecated `Divider` (→ `HorizontalDivider`), deprecated `Locale(String)` ctors | build warnings list | Mechanical fix pass; then treat warnings as errors in CI |

### 3.6 Testing

| # | Sev | Finding | Where | Fix |
|---|---|---|---|---|
| T1 | P1 | All 4 test files are AI‑Studio boilerplate: `assertEquals(4, 2 + 2)`, `useAppContext()`, a Robolectric string read, a screenshot of `TutorialTab`. CI's test gate is meaningless. | `app/src/test/`, `app/src/androidTest/` | Delete the "Example" tests. Add real ones (§7). |
| T2 | P1 | Nothing testable in isolation — logic is welded to `Application`, globals, and Compose | see A2/A3 | Extraction (A2) unlocks unit tests for: `SrsScheduler`, JSON repair, error classification, PDF chunk sizing, TTS locale mapping, dedupe |
| T3 | P2 | Roborazzi + Robolectric + `roborazzi.compose` are wired but only screenshot `TutorialTab` (which has no state) | `GreetingScreenshotTest.kt` | Keep Roborazzi; snapshot the real screens in known states once they're extractable |

### 3.7 Docs & process

| # | Sev | Finding | Fix |
|---|---|---|---|
| P1 | P1 | No `CLAUDE.md`, no `docs/`, no architecture notes, no ADRs | Create `CLAUDE.md` (invariants) + `docs/architecture/` (port Slim's structure — §6.2) |
| P2 | P2 | 6 commits total, all large ("extend repo with github action pipeline" ×3); `release.yml` assumes Conventional Commits but history doesn't follow it | Adopt Conventional Commits (skill available) + small PRs going forward |
| P3 | P2 | `.env.example` documents `GEMINI_API_KEY` but the whole secrets flow needs rethinking (AI5) | Rework alongside AI5 |
| P4 | P3 | `metadata.json` (AI‑Studio artefact) still in repo | Keep for now (harmless); revisit |

---

## 4. Proposed target architecture

```
app/
  di/                      AppContainer or Hilt modules
  data/
    local/
      AppDatabase.kt       exportSchema=true, real migrations
      dao/  entity/        Long PKs, unique indexes
    remote/
      GeminiService.kt     Retrofit iface, header auth
      dto/  ErrorParsing.kt
    repository/
      DeckRepository.kt
      ImportRepository.kt      ← PDF extract + chunk + parse + save/merge
      TutorRepository.kt
      SettingsRepository.kt    ← DataStore
      GamificationRepository.kt ← Room
    pdf/  PdfTextExtractor.kt   (PdfBox-Android, Apache-2.0)
  domain/
    model/                 Deck, Flashcard, StudyCard, TutorTurn …
    srs/  SrsScheduler.kt   pure, unit-tested
    import/ ImportUseCase.kt
  ui/
    theme/                 real tokens, dark + dynamic
    navigation/  AppNavHost.kt
    decks/  DecksScreen.kt  DecksViewModel.kt
    study/  StudyScreen.kt  StudyViewModel.kt (thin) InteractiveFlashcard.kt
    import/ ImportScreen.kt ImportViewModel.kt
    tutor/  TutorSheet.kt   TutorViewModel.kt
    quest/  QuestScreen.kt
    tutorial/ TutorialScreen.kt
    settings/ ApiKeyDialog.kt LearningGoalDialog.kt
    diagnostics/ DiagnosticsDialog.kt  (debug-only)
    components/  design-system atoms
  MainActivity.kt          ~80 lines: theme + NavHost
prompts/                   system + user prompt templates
```

Guiding rules:
- **One screen per file.** No file over ~400 lines.
- **ViewModels are state holders**, not service layers. All I/O behind repositories.
- **Domain logic is pure and unit‑tested** (`SrsScheduler`, import chunking, error parsing).
- **No global singletons** — everything injected.
- **State that must survive process death → `SavedStateHandle`;** transient UI → `rememberSaveable`.
- **The key never ships** (proxy) — or only the user's own key, encrypted.
- **Import never fabricates content.**

---

## 5. Phased execution plan

Each phase ends with a **green** `./gradlew lintDebug testDebugUnitTest assembleDebug` and a tag.

### Phase 0 — Unblock (≈ half a day) · *do before anything else*
- [ ] `local.properties` documented in README
- [ ] Delete `debugConfig`; default debug signing (B1)
- [ ] Rewrite `build.yml`: lint + unit test + `assembleDebug` + upload artifact. **Delete** the GitHub Pages steps. (B2, B4)
- [ ] Rewrite `release.yml`: on push to `main`, build **signed** release APK from CI secrets, publish to GitHub Releases as `adaptiveflow-<version>.apk` + `adaptiveflow-latest.apk`. Drop release‑please for now. (B2, B3, B5)
- [ ] Add the 4 GitHub secrets (`SIGNING_KEY_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- [ ] Delete all Firebase deps + `google-services` plugin (B8)
- [ ] Swap `itextg` → `com.tom-roush:pdfbox-android` (fix the `libs.versions.toml` mispoint) (B6)
- [ ] Enable R8 + resource shrinking for `release`; add Moshi/Room/Retrofit keep rules (B7)
- [ ] Pin AGP + `compileSdk` to stable (B9)
- **Exit criteria:** first green CI run in project history; a real signed APK on the Releases page.

### Phase 1 — Foundation & honesty (≈ 3–4 days)
- [ ] Rename identity in lockstep: `namespace` / `applicationId` / package / `rootProject.name` / theme (B10)
- [ ] Real `AdaptiveFlowTheme` — dark + dynamic wired; move the gradient into the theme (U2)
- [ ] **Remove the fake "Gemini Nano"** — no‑key state = "AI features need a key" + honest offline path (list parsing only) (AI1, AI2)
- [ ] Import **fails loudly** — never substitutes canned decks (AI2)
- [ ] Gemini auth: key in `x-goog-api-key` header; correct current model ids; drop the fake "preferred" (AI3, AI4)
- [ ] Decide the key story (AI5): **(a)** proxy backend, or **(b)** user‑key‑only + `EncryptedSharedPreferences`, no bundled key. **← needs your decision, see §8.**
- [ ] `exportSchema = true`, commit baseline schema, destructive fallback → debug‑only (D1)
- [ ] Seed "Master Vocabulary Pool" once via `has_seeded` flag + unique index (D2)
- [ ] Delete boilerplate tests; add first real unit tests (SRS, JSON repair, TTS locale) (T1)
- [ ] `CLAUDE.md` + `docs/architecture/` seeded (port from Slim — §6.2)
- **Exit criteria:** app is honest about its capabilities; no data‑loss on upgrade; key not trivially extractable; lint + real tests green.

### Phase 2 — Architecture decomposition (≈ 1 week)
- [ ] Add DI (Hilt or `AppContainer`) (A3)
- [ ] Extract repositories + `SrsScheduler` + `ImportUseCase` + `TtsController` from the VM (A2)
- [ ] Adopt `navigation-compose`; `MainActivity` → `NavHost` (A4)
- [ ] Split `MainActivity.kt` into `ui/<feature>/` — one screen per file (A1)
- [ ] `rememberSaveable` + `SavedStateHandle` everywhere state must survive (A5, U4)
- [ ] Consolidate persistence: settings → DataStore, gamification → Room (D4)
- **Exit criteria:** no file over ~400 lines; screens previewable; VM unit‑testable; rotation is lossless.

### Phase 3 — Correctness & UX (incremental)
- [ ] One SRS model, pure + tested (D6, D7)
- [ ] Structured Gemini error parsing (AI6); proper multi‑turn tutor `contents` (AI10)
- [ ] Explicit, reversible deck merge with a preview (D5)
- [ ] Quiz distractors from same deck only (D8)
- [ ] Session‑complete tracks answered, not flipped (U6)
- [ ] `OpenDocument` with MIME filter (U7); deprecation warning pass (U9); consistent Snackbars (U8)
- [ ] Design‑system pass: kill the inline `Color`/`dp`/string literals feature by feature (U1)
- [ ] Roborazzi snapshots of real screens (T3)

### Phase 4 — Release polish
- [ ] Real launcher icon + `strings.xml` fully populated + Play‑store‑grade README
- [ ] Backup rules decided (B14); privacy note for the Gemini calls
- [ ] Reinstate release‑please **with** its config files, or a manual version bump flow, once commits are conventional
- [ ] Landing page (port Slim's `scripts/landing-page.html` pattern) if a Pages site is wanted

---

## 6. What to port from Slim (`../Slim`, `PlayFoundryHQ/Slim`)

Slim is a small, disciplined Android app with a green pipeline, real docs, and a
hard‑won set of invariants. Concrete things to lift:

### 6.1 CI/CD — the two‑workflow pattern
- **`Slim/.github/workflows/android-build.yml`** → AdaptiveFlow `build.yml`.
  Steps: checkout · JDK 17 (temurin) · `chmod +x gradlew` · `./gradlew lintDebug` ·
  `./gradlew testDebugUnitTest` · `./gradlew assembleDebug` · upload the debug APK
  artifact (`if-no-files-found: error`). **No Pages deploy in the build job.**
- **`Slim/.github/workflows/release.yml`** → AdaptiveFlow `release.yml`.
  Pattern: on push to `main` touching `app/**`, decode `KEYSTORE_BASE64` secret →
  `$RUNNER_TEMP/release.keystore`, `./gradlew assembleRelease` with
  `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from
  secrets, then `gh release create/upload` with **two asset names**: a
  versioned one (`adaptiveflow-v1.2.3.apk`) and a stable one
  (`adaptiveflow-latest.apk`) that the landing page can always link to.
  `concurrency: group: release, cancel-in-progress: false`.
- Slim's `app/build.gradle` signing block is the exact "CI‑only release signing,
  local falls back to debug" pattern to copy (§2.2).

### 6.2 Documentation structure
- **`Slim/CLAUDE.md`** — a short, dense "how this codebase works + non‑negotiable
  invariants" file. Create AdaptiveFlow's with, at minimum:
  - the identity‑lockstep rule (namespace/appId/package/theme)
  - "import never fabricates content"
  - "no `fallbackToDestructiveMigration` in release"
  - "the API key never ships / only the user's own, encrypted"
  - "ViewModels are state holders, not service layers"
  - "state that must survive process death → SavedStateHandle"
- **`Slim/docs/architecture/system_requirements.md`** + **`settings_and_features.md`**
  — port the format: a permissions/capabilities section, a feature‑by‑feature
  spec, a gesture/interaction table, and `[!NOTE]` blocks for platform limits.
  AdaptiveFlow needs: the AI capability matrix (key present / absent / proxy),
  the SRS model spec, the import pipeline spec, the gamification rules.

### 6.3 Release signing hygiene
Slim: keystore lives **only** in GitHub secrets as base64; CI decodes to a temp
file; local dev never needs it. AdaptiveFlow's committed intent (`debugConfig`
pointing at a gitignored file, README telling you to hand‑edit gradle) is the
anti‑pattern.

### 6.4 R8 / ProGuard discipline
Slim keeps `minifyEnabled = false` **only** until verified on‑device, documents
why, and keeps explicit Room keep rules. AdaptiveFlow should enable R8 now
(Phase 0) with keep rules for Moshi generated adapters, Room, and Retrofit, and
verify on a device before the first real release.

### 6.5 Package identity
Slim's lesson (its PR #3): `com.opscalehub.slim` → `io.github.playfoundryhq.slim`
— reverse‑DNS of the GitHub Pages host, F‑Droid convention, renamed in lockstep
across `namespace` / `applicationId` / Kotlin package / manifest / F‑Droid
metadata. AdaptiveFlow: `com.example` + `com.aistudio.adaptiveflow.zqyhmp` → pick
one real id (e.g. `io.github.opscalehub.adaptiveflow`) and do the same lockstep
rename. **Decide before the first Release** — changing `applicationId` later is a
different app with no in‑place upgrade.

### 6.6 Working method
- **Small PRs, green gate each time** (Slim shipped ~10 in a session; AdaptiveFlow
  has 6 giant commits).
- **Conventional Commits** (skill available) — and `release.yml` already assumes
  them.
- **Verify against the running app, not just a passing test** — screenshot the
  screen, run it on a device. (Slim caught real bugs this way that unit tests
  missed.)
- **Memory notes** — this session already has
  `~/.claude/projects/-home-opsquad-Workspace-adaptiveflow/memory/`; use it for
  durable decisions (key strategy, model ids, identity).

### 6.7 What does *not* port
Slim is a launcher; its ANR/focus/window invariants, `AppWidgetHostView` rules,
and gesture system are launcher‑specific and irrelevant here. Take the
**process and structure**, not the launcher internals.

---

## 7. First real tests to write (Phase 1)

Pure‑JVM unit tests, no Android:
- `SrsScheduler` — table of `(confidence, card state) → (ease, interval, nextReview)` over known SM‑2 vectors; boundary cases (ease floor 1.3, ceiling 3.0; interval ≥ 1).
- `cleanAndExtractJson` / `robustParseJsonDeck` — markdown‑fenced JSON, trailing prose, JSON array vs object, malformed (must throw, not fabricate).
- Gemini error classification — feed real error bodies (429 with `retryDelay`, 400 malformed, 403 bad key, 413) → correct category + retry delay. *(Use the `verify‑threshold‑regex‑boundary‑logic` skill.)*
- `getLocaleFromLanguageName` — "Farsi"/"fa"/"persian" → `fa`; unknown → default.
- PDF chunk sizing — `extractedPageTexts.size` → expected `chunkSize` at each boundary (5, 15, 30, 60).
- `getMultipleChoiceOptions` — never returns the correct answer twice; handles < 4 cards.

Instrumented / Robolectric:
- Room migrations (once real migrations exist).
- `ImportRepository` end‑to‑end with a fake `GeminiService` (success, malformed, 429, empty).

---

## 8. Decisions needed from you

1. **Gemini key strategy** (blocks Phase 1): (a) stand up a proxy backend so the
   key never ships, or (b) ship with **no** bundled key — user pastes their own,
   stored encrypted. `metadata.json` says `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`
   which implies (a) was the original intent (Firebase AI Logic).
2. **The "Gemini Nano" story**: confirm we **remove the fake** and either (i) show
   "needs a key" honestly, or (ii) invest in a real on‑device path later
   (ML Kit GenAI / AICore).
3. **Package identity**: `io.github.opscalehub.adaptiveflow`? Or a different org
   (Slim moved off `OpScaleHub` → `PlayFoundryHQ`; is AdaptiveFlow moving too)?
4. **Toolchain risk appetite**: pin down to stable AGP/SDK (recommended) vs. stay
   on AGP 9.1 / SDK 36.1 bleeding edge.
5. **Release cadence**: manual version bumps now, reinstate `release-please` once
   commits are conventional — or set up `release-please` properly in Phase 0.
6. **Landing page / GitHub Pages**: wanted? If yes, port Slim's
   `scripts/landing-page.html` pattern; if no, delete the Pages plumbing entirely.

---

## 9. Appendix — repo metrics (2026‑09‑09)

| Metric | Value |
|---|---|
| Total Kotlin (main) | ~8,300 lines |
| `MainActivity.kt` | 5,298 lines / 20 composables |
| `StudyViewModel.kt` | 1,961 lines |
| Hardcoded `Color(0x…)` | 505 |
| `stringResource()` calls | 0 |
| `strings.xml` entries | 1 |
| `rememberSaveable` | 0 |
| `SharedPreferences` call sites (VM) | 17 |
| Debug APK size / dex files | 24.7 MB / 10 |
| Real unit tests | 0 |
| Green CI runs, all time | 0 |
| Dead dependency groups | Firebase (4 artifacts + plugin) |
| Licensing risk | iText 5 (AGPLv3) |
| Git commits | 6 |

**Build environment (this machine):** JDK 17.0.18 · Android SDK at
`~/Android/Sdk` with platforms 34/35/36.1, build‑tools 34/35/36.0.0 · no
`ANDROID_HOME` set · no `local.properties`.

**Remote:** `git@github.com:OpScaleHub/adaptiveflow.git` · branch `main` · clean.

---

## 10. Progress log

### 2026-09-09 — branch `feat/stabilize-and-multi-provider-ai`  (Phase 0 + Phase-1 essentials)

**Build / release (Phase 0)**
- ✅ B1 — deleted the broken `debugConfig`; default debug signing. Clean clone builds.
- ✅ B2/B4/B5 — replaced both copied workflows with **one** `.github/workflows/ci.yml`
  (verify on PR/push; signed-release + GitHub Release on `main`, path-filtered,
  `concurrency` dedupe). No release-please, no Pages-deploy-from-missing-dir.
- ✅ B6 — iText 5 (AGPL) → **PdfBox-Android** (`com.tom-roush:pdfbox-android`, Apache-2.0);
  `data/pdf/PdfTextExtractor.kt`; `AdaptiveFlowApp` inits `PDFBoxResourceLoader`.
- ✅ B7 — R8 + `shrinkResources` on for `release`; full `proguard-rules.pro`
  (OkHttp / Retrofit / Moshi / Room / Tink / PdfBox keep+dontwarn).
- ✅ B8 — deleted **all** Firebase (`firebase-*`, `google-services` plugin,
  `googleServices{}`, `googleServices.missing.passthrough`) and the
  `secrets-gradle-plugin` (+ `.env.example`). `libs.versions.toml` pruned of
  ~15 unused entries.
- ✅ B10 — package `com.example` → `io.github.playfoundryhq.adaptiveflow`
  (namespace + applicationId + every Kotlin package + theme rename
  `MyApplicationTheme`→`AdaptiveFlowTheme`, `Theme.MyApplication`→`Theme.AdaptiveFlow`).
  `rootProject.name` → `AdaptiveFlow`. Remote → `PlayFoundryHQ/adaptiveflow`.
- ✅ B11 — JVM 17 (`compileOptions` + `jvmToolchain(17)`).
- ✅ B14 — `EncryptedSharedPreferences` files excluded from backup / device transfer.
- versionName `1.0` → **`0.1.0`** (signals pre-1.0).

**AI layer + honesty (Phase 1)**
- ✅ AI1/AI2/AI3 — **removed the fake "Gemini Nano"** entirely
  (`generateThematicCardsLocally`, `importViaGeminiNano`, the canned-chat block,
  all "on-device AI" copy). Import with no key / on AI failure now **errors
  honestly** or uses the real offline `parseRawTextLocally` — never fabricates cards.
- ✅ **Decision 1** — provider abstraction: `data/ai/` — `AiProvider` interface,
  `GeminiProvider`, `DeepSeekProvider`, `AiClient`, normalised `AiException.Kind`.
  Settings dialog rebuilt with a Gemini/DeepSeek toggle + per-provider key.
- ✅ AI4 — Gemini auth moved to `x-goog-api-key` **header** (was `?key=` URL param).
- ✅ AI5 — **no bundled key.** User-supplied only, per-provider, in
  `EncryptedSharedPreferences` via `SettingsStore`. `BuildConfig.GEMINI_API_KEY` gone.
- ✅ AI6 — structured error parsing in the providers (`error.status`, `retryDelay`),
  not message string-matching in the VM.
- ✅ AI7 — prompt templates → `ai/Prompts.kt`, parameterised, de-duplicated.
- ✅ AI9 — OkHttp timeouts 120s→(20 connect / 90 read / 150 call-cap).
- ✅ AI10 — tutor history now uses real `role: user/model` turns.

**Data (Phase 1)**
- ✅ D1 — `exportSchema = true`, `app/schemas/…/1.json` committed,
  `fallbackToDestructiveMigration` **debug-only**, `MIGRATIONS` array ready.
- ✅ D2 — Master Vocabulary Pool seeded **once** via `settings.hasSeeded` +
  snapshot check (no insert-inside-collect race).
- ✅ D4 (partial) — settings/keys/goal/TTS consolidated in `SettingsStore`
  (gamification counters still there interim; Room move deferred).
- ✅ D8 — quiz distractors now same-deck only (dropped the hardcoded English list).
- ✅ A6 — `allDecks` via `stateIn` instead of `(x as MutableStateFlow)`.
- ✅ A7 — dropped the needless `withContext(Main)` wrappers around StateFlow writes.

**Domain + tests**
- ✅ Extracted `domain/SrsScheduler.kt` (pure) + `SrsSchedulerTest` (5 cases).
- ✅ `GeminiRetryDelayTest` (3 cases). Deleted the 4 boilerplate "Example" tests.
- **8 unit tests, all green.** `lintDebug` + `assembleDebug` green.

**Docs**
- ✅ `CLAUDE.md` (invariants), `docs/architecture/overview.md`, README rewritten.

### 2026-09-09 — PR #4 `feat/session-state-and-polish` (v0.1.3) — Phase 2 slice 1

- **A5 / process death** — `StudyViewModel` takes a `SavedStateHandle`;
  `selectDeck`/`clearActiveDeck` persist+clear the active deck id; `init`
  reopens an in-progress session with a fresh queue after a kill. Default
  `SavedStateViewModelFactory` injects it — no `MainActivity` change.
- **U-x1 fixed** — dead in-session `N / total` counter replaced with a live
  session score (✓ correct ✗ wrong); redundant frozen blue bar removed.
- **U-x2 fixed** — `friendlyAiError(AiException)` → actionable provider-aware
  copy, wired into the tutor + import error paths.
- Version footer in the Guide tab (`BuildConfig`).
- Still pending in Phase 2: DI, navigation-compose + back stack, per-screen
  ViewModels, gamification→Room, Int→Long PKs, one SRS model, reversible merge.

### 2026-09-09 — PR #3 `fix/copy-honesty` (merged, v0.1.2) + on-device verification

**PR #3 — copy honesty (invariant #3):** rewrote UI strings that claimed
features the app lacks — "Predictive Context / predicts source+target in
real-time", "ZERO TEXT CONFIGURATION", Decks banner "local fallback
assistance", YouTube "Gemini analyzes the transcript", Paste "generates
pronunciation guides". De-vendored hard-coded "Gemini" in shared copy.
Strings only, no logic change.

**First real on-device run (OnePlus, adb, v0.1.2 release APK):**
- ✅ In-place upgrade 0.1.1→0.1.2; launches clean; Master Pool + study goal
  survived the upgrade.
- ✅ Honest copy is live on Decks / Import / Guide.
- ✅ Offline import: a `word: meaning` list → 6-card deck tagged "Auto",
  **no API call, no fabrication** (invariant #2 holds).
- ✅ Study loop: flip → reveal → LOW/MEDIUM/HIGH confidence grading.
- ✅ SRS writes persist to Room — dashboard showed 2/6 learned · 4 due
  after two HIGH grades, across a background/relaunch.
- ✅ **AI tutor makes a real DeepSeek call** — Bearer auth, request,
  response parse, and `AiException` normalisation all work end-to-end.
  The account returned HTTP 402 "Insufficient Balance"; the app surfaced
  it honestly rather than faking a reply. Needs DeepSeek credit or a
  Gemini key to complete a generation.
- 🐛 **U-x1**: in-session `1 / 6` counter + its blue progress bar are
  frozen at 1/6; the green "N of 6 flipped" bar advances correctly.
  Cosmetic. → Phase 3.
- 🐛 **U-x2**: provider errors render as a bare grey chip ("Insufficient
  Balance") with no guidance. Add "top up, or switch provider in
  Settings". → Phase 3.
- ◽ **A-x3**: background→relaunch drops the in-progress session back to
  the Decks list (session state not in `SavedStateHandle`). Expected —
  fixed by the Phase 2 nav/SavedStateHandle work.
- ◽ **AI-x4 (review)**: "Instant Quick-Start Sample Decks" still insert
  canned decks. User-initiated + labelled "Sample", so not an invariant-2
  break, but decide whether they stay.
- ◽ Not verified: rotation / process-death `rememberSaveable` — this
  ColorOS device blocks the adb secure-settings needed; check by hand.

### 2026-09-09 — PR #2 `refactor/split-ui-and-save-state` (merged, v0.1.1)

- **A1** — `MainActivity.kt` **5,258 -> 249 lines**. 7 new `ui/*.kt` files, one
  per feature (Tutorial / Decks / Import / Study / TutorSheet / Quest /
  SettingsDialogs). Pure move, no logic change. All in package `…ui` so they
  see each other with no cross-imports.
- **A5 / U4 (partial)** — `rememberSaveable` for `activeTab` (no more
  bounce-to-Decks on rotate), the Import screen's text inputs + choices, and
  the Study screen's sheet/drawer flags.
- **A9** — `DiagnosticLogger.getLogcatLogs()` is a no-op outside debug.
- Still large single features: `ImportScreen` ~1.4k, `StudyScreen` ~1.5k — break
  their sub-components out in Phase 3.
- Kotlin unused-import warnings in the new files (superset import blocks) —
  spotless/ktlint pass will strip them.

### Deferred to the next PR (Phase 2 — architecture)
`MainActivity.kt` split · DI · `navigation-compose` + back stack · `rememberSaveable`
/ `SavedStateHandle` everywhere · gamification → Room · `Int`→`Long` PKs (D3) ·
one SRS model (D6) · design-system pass (U1) · dark mode (U2) · deprecation
warnings (U9) · explicit reversible merge (D5).

### Tooling note — Archify
`https://tt-a1i.github.io/archify/` — MIT-licensed Claude Code **skill**
(`npx skills add tt-a1i/archify -g`) that turns prose into self-contained
interactive HTML/SVG diagrams (architecture / workflow / sequence / data-flow /
lifecycle), 4× export. **Verdict: adopt for the landing page (Phase 4).** Good
fit for the AI-provider diagram, the import-pipeline data-flow, and the CI
workflow — outputs drop straight into GitHub Pages. Treat generated diagrams as
review-then-keep drafts, and only draw them once the architecture settles
(post Phase 2) so they reflect reality.
