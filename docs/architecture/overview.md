# AdaptiveFlow — architecture overview

> Snapshot: **v0.7.1** (2026-09). The stabilise → decompose → localise passes are
> done. `MainActivity.kt` is ~200 lines (a `NavHost` + bottom bar); each screen
> is its own file; import/merge/SRS/TTS/DI are extracted; the UI ships in English
> and Persian. The original audit and its history live in `PLAN.md`.

## Layers

```
┌───────────────────────────────────────────────────────────────┐
│ UI (Compose, single-Activity)                                 │
│  MainActivity                                                 │
│   └ NavHost  decks · quest · import · guide   (bottom bar)    │
│              study                            (bar hidden)    │
│   DecksScreen · QuestScreen(PathTab) · ImportScreen ·         │
│   TutorialScreen · StudyScreen · TutorSheet · SettingsDialogs │
│  StudyViewModel   one AndroidViewModel, ~540 lines — screen   │
│                   state + session logic; heavy work delegated │
│                   to domain/ + data/                          │
│  attachBaseContext → LocaleManager.wrap  (per-app UI locale)  │
└───────────────┬───────────────────────────────────────────────┘
                │ StateFlow / function calls
┌───────────────▼───────────────────────────────────────────────┐
│ di/AppContainer   manual DI, every field `by lazy`            │
│   database · repository · settings · aiClient · tts           │
├───────────────────────────────────────────────────────────────┤
│ domain/ (pure, unit-tested)                                   │
│   SrsScheduler        SM-2 variant, next(State,Grade)→State   │
│   ImportPipeline      import + staged merge (~415 lines)      │
│   ImportParsing       JSON repair · offline list parser · URL │
│   DeckMerge           merge planner (new / enrich / skip)     │
│   Languages           curated list · endonyms · rtl · uiTx    │
├───────────────────────────────────────────────────────────────┤
│ data/                                                         │
│   StudyRepository ─► Room (Deck / Flashcard / ChatLog /       │
│                       Progress)   schema v3, real migrations  │
│   SettingsStore   ─► EncryptedSharedPreferences (keys)        │
│                      + plain prefs (everything else)          │
│   LocaleManager   ─► plain prefs (app_locale tag)             │
│   DeckExporter    ─► JSON / CSV                               │
│   PdfTextExtractor─► PdfBox-Android                           │
│   AiClient        ─► AiProvider: GeminiProvider · DeepSeek    │
│   Prompts             all templates, parameterised            │
│   tts/TtsController   app-scoped, background engine bind      │
└───────────────────────────────────────────────────────────────┘
```

### Dependency injection

`di/AppContainer(app)` — a small manual container, all fields `by lazy` so
`AdaptiveFlowApp.onCreate` stays cheap (EncryptedSharedPreferences / Tink / OkHttp
/ the TTS engine are only built on first use). `AdaptiveFlowApp` holds the single
instance; `StudyViewModel.Factory` reads `APPLICATION_KEY` + `createSavedStateHandle()`
and pulls collaborators off `app.container`.

### Navigation

`navigation-compose`. `MainActivity` hosts a `NavHost` with `decks / quest /
import / guide` on the bottom bar and a separate `study` route with the bar
hidden. Tab switches use `popUpTo(startDestination){ saveState = true } +
launchSingleTop + restoreState`. `viewModel.currentDeck` is the source of truth
for entering/leaving study; a `LaunchedEffect(currentDeck)` navigates to/from the
`study` route and system-back returns to the deck library.

## The AI layer (`data/ai/`)

Everything above the transport talks to **`AiProvider`** and never to a vendor
type.

| | Gemini | DeepSeek |
|---|---|---|
| Endpoint | `generativelanguage.googleapis.com` `:generateContent` | `api.deepseek.com` `/chat/completions` (OpenAI-shaped) |
| Auth | `x-goog-api-key` header | `Authorization: Bearer` header |
| Structured JSON | `responseMimeType: application/json` | `response_format: {type: json_object}` |
| Multi-turn | `contents[].role` user/model | `messages[].role` user/assistant |
| PDF bytes inline | ✅ (`inlineData`) | ❌ — scanned PDFs rejected with a "switch to Gemini" message |
| Default model | `gemini-2.5-flash` | `deepseek-chat` |

Failures normalise to **`AiException(kind, retryAfterMs)`** with
`kind ∈ {AUTH, RATE_LIMIT, TRANSIENT, PAYLOAD_TOO_LARGE, BAD_REQUEST, NETWORK,
EMPTY, UNKNOWN}`. `StudyViewModel.friendlyAiError()` turns a `kind` into a short,
actionable, **localised** sentence — it never string-matches vendor prose.

Keys: **user-supplied only**, one per provider, in `EncryptedSharedPreferences`.
No key bundled in the APK.

## The import pipeline (`domain/ImportPipeline`)

```
input (text / URL / file)
  │
  ├─ pasted / loaded deck JSON?       → save directly                (offline)
  ├─ plain "word: meaning" list?      → ImportParsing.parseRawTextLocally → save (offline)
  │
  ├─ no AI key configured?            → ImportState.Error (honest)
  │
  ├─ PDF with selectable text         → PdfTextExtractor → chunk by page-count
  │                                     → processPagesChunkRecursive
  │                                        (per-chunk parse; on PAYLOAD_TOO_LARGE
  │                                         split + recurse; RATE_LIMIT/TRANSIENT
  │                                         → back-off + retry)
  │                                     → merge chunk decks → saveOrMergeCards
  │
  ├─ scanned PDF (no text) + Gemini   → send bytes inline, single-shot parse
  ├─ scanned PDF + DeepSeek           → Error ("switch to Gemini")
  │
  └─ text / YouTube-with-hint         → single-shot structured parse
                                        → ImportParsing.filterOutUrlEchoCards
                                        → Error if zero real cards (never fabricate)
                                        → saveOrMergeCards
```

`saveOrMergeCards` either creates a new deck or, when a target deck is chosen (or
the deck name is the Master Pool), builds a **`DeckMerge` plan** and surfaces
`ImportState.MergePreview` (N new · M enriched · K skipped) → the user confirms →
`ImportState.Success` with an **undo** token. Re-importing an export of the
Master Pool routes to the existing pool, never a second copy.

`ImportPipeline` and `ImportParsing` are constructed with the bits they need
(`appContext`, `moshi`, provider/native/target lambdas) so the parsing and merge
logic is unit-tested (`ImportParsingTest`, `DeckMergeTest`) without the whole
pipeline.

## SRS (`domain/SrsScheduler`)

Pure `next(State, Grade) → State`. `Grade` maps from the confidence buttons:
`LOW→AGAIN`, `MEDIUM→GOOD`, `HIGH→EASY`.

- AGAIN: `repetitions → 0`, interval `→ 1`, ease `−0.25` (floor 1.3)
- GOOD: interval ladder `1 → 3 → round(interval × ease, min 6)`, ease unchanged
- EASY: same ladder × 1.4, ease `+0.15` (ceiling 3.0)

On top of the `nextReview` math, the ViewModel re-orders the **in-session** queue
by confidence so a struggled card resurfaces near the front. These are two
deliberate layers, not two schedulers fighting — the pure one owns persistence,
the session one owns "what you see next right now".

## Persistence

| Data | Store | Notes |
|---|---|---|
| Decks, flashcards, chat logs, **progress** (xp / streak / lastStudyDate) | Room (`adaptive_flow_database`), schema **v3** | `exportSchema = true`; `MIGRATION_1_2` adds `Progress`, `MIGRATION_2_3` is a no-op (Int→Long PKs). One-shot copy of legacy XP/streak from `SharedPreferences`, guarded by `SettingsStore.progressMigratedToRoom`. |
| AI provider choice, learning goal, `goalConfigured`, TTS prefs, `app_locale` | plain `SharedPreferences` (`adaptiveflow_prefs`) | |
| API keys | `EncryptedSharedPreferences` (`adaptiveflow_secure`) | excluded from backup; falls back to plain on a broken keystore rather than crashing |
| In-flight session state (deck, card index, session counters) | `SavedStateHandle` in `StudyViewModel` | survives process death |

## Localisation

- **Catalogues:** `res/values/strings.xml` (English) + `res/values-fa/strings.xml`
  (Persian) — identical `name=` sets and identical format-arg positions
  (lint `StringFormatMatches` enforces it).
- **The one control:** the goal dialog's *my language* picker. If the chosen
  `Languages` entry has `uiTranslation = true`, `LocaleManager.apply(activity,
  Languages.uiLocaleTag(name))` persists the BCP-47 tag and `recreate()`s the
  Activity; otherwise the UI stays English and only the AI prompts change.
- **How it applies:** `MainActivity.attachBaseContext` →
  `LocaleManager.wrap(base)` builds a `Configuration` with the stored locale +
  `setLayoutDirection`, via `createConfigurationContext`. Min SDK 24, **no
  AppCompat, no theme change** — the deliberately low-risk route. Android-13
  system "App languages" integration (`localeConfig` + AppCompat's
  `setApplicationLocales`) is deferred (PLAN.md §11).
- **RTL:** the codebase was already clean — `supportsRtl`, no absolute
  `left`/`right`, every directional icon `Icons.AutoMirrored`. The Low/Med/High
  rating row is explicitly pinned LTR so its colour order never inverts.
- **Not localised** (English by design): `Prompts.kt`, the tutor quick-action
  prompts, the copyable "external AI" prompt template, `DiagnosticLogsDialog`.

## Text-to-speech (`tts/TtsController`)

App-scoped, created lazily by `AppContainer`. `prepare()` binds the engine on a
`"tts-init"` background thread (the bind can stall ~2 s). Prefers
`com.google.android.tts`, else the system default. `bestVoiceFor(locale)` picks
the highest-quality non-`NOT_INSTALLED` voice; `missingLanguage` drives an
`ACTION_INSTALL_TTS_DATA` hint on the study screen. `cleanForSpeech` strips
markup / normalises separators. `UtteranceProgressListener` feeds `isSpeaking`.

**Voice quality is the current weak point** — the system engines are mediocre for
Swedish and only adequate for English. Options (on-device neural via
Sherpa-ONNX / Piper, the user's own Gemini key for a premium tier, AvaCore for
Persian) are laid out in [`../tts-options.md`](../tts-options.md).

## Testing

`app/src/test/` — real unit tests, no boilerplate:

| File | Covers |
|---|---|
| `SrsSchedulerTest` | the scheduling ladder + ease bounds |
| `ImportParsingTest` | JSON fence stripping / repair, bare-array decks, the offline list parser, URL-echo filtering, YouTube detection |
| `DeckMergeTest` | normalisation, insert vs enrich vs skip, no-op re-import, counts |
| `GeminiRetryDelayTest` | `Retry-After` / back-off parsing |
| `LanguagesTest` | tolerant lookup, `isRtl`, `shortLabel`, `uiLocaleTag` |
| `ScreenshotTest` | Roborazzi — Guide (light + dark) + a pillar item; also a headless render crash smoke-test |

Gate: `./gradlew lintDebug testDebugUnitTest assembleDebug`.
