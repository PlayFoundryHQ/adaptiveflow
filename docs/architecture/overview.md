# AdaptiveFlow — architecture overview

> Snapshot: 2026-09-09, after the "stabilise + multi-provider" pass. The UI layer
> (`MainActivity.kt`) is still a single large file — the decomposition into
> `ui/<feature>/` + navigation + DI is the next pass (PLAN.md Phase 2).

## Layers

```
┌─────────────────────────────────────────────┐
│ UI (Compose)                                │
│  MainActivity → MainScreen                  │
│   DecksTab · PathTab(Quest) · ImportTab ·   │
│   TutorialTab · StudySessionScreen ·        │
│   AiTutorBottomSheet · dialogs              │
│  StudyViewModel  (one VM today; per-screen  │
│                   VMs in Phase 2)           │
└───────────────┬─────────────────────────────┘
                │ StateFlow / function calls
┌───────────────▼─────────────────────────────┐
│ Domain / data                               │
│  SrsScheduler         pure SM-2 variant     │
│  StudyRepository ──► Room (Deck/Flashcard/  │
│                       ChatLog)              │
│  SettingsStore ──► EncryptedSharedPrefs     │
│                     (keys) + plain prefs    │
│  PdfTextExtractor ──► PdfBox-Android        │
│  AiProvider ──► GeminiProvider              │
│              └► DeepSeekProvider            │
│  Prompts             all templates          │
└─────────────────────────────────────────────┘
```

## The AI layer (`data/ai/`)

Everything above the transport talks to **`AiProvider`** and never to a vendor
type. Two implementations today:

| | Gemini | DeepSeek |
|---|---|---|
| Endpoint | `generativelanguage.googleapis.com` `:generateContent` | `api.deepseek.com` `/chat/completions` (OpenAI-shaped) |
| Auth | `x-goog-api-key` header | `Authorization: Bearer` header |
| Structured JSON | `responseMimeType: application/json` | `response_format: {type: json_object}` |
| Multi-turn | `contents[].role` user/model | `messages[].role` user/assistant |
| PDF bytes inline | ✅ (`inlineData`) | ❌ — scanned PDFs rejected with a "switch to Gemini" message |
| Default model | `gemini-2.5-flash` | `deepseek-chat` |

Failures from either provider normalise to **`AiException(kind, retryAfterMs)`**
where `kind ∈ {AUTH, RATE_LIMIT, TRANSIENT, PAYLOAD_TOO_LARGE, BAD_REQUEST,
NETWORK, EMPTY, UNKNOWN}`. Callers branch on `kind`, never on message text.

Keys: **user-supplied only**, one per provider, in `EncryptedSharedPreferences`.
Selected provider + keys live in `SettingsStore`. No key bundled in the APK.

## The import pipeline (`StudyViewModel.importDeckFromRawText`)

```
input (text / URL / file)
  │
  ├─ pasted ready-made deck JSON?     → save directly
  ├─ plain "word: meaning" list?      → parseRawTextLocally → save   (offline, no key)
  │
  ├─ no AI key configured?            → ImportState.Error (honest)
  │
  ├─ PDF with selectable text         → PdfTextExtractor → chunk by page-count
  │                                     → processPagesChunkRecursive
  │                                        (per-chunk parse; on PAYLOAD_TOO_LARGE
  │                                         split the chunk and recurse;
  │                                         RATE_LIMIT/TRANSIENT → backoff + retry)
  │                                     → merge chunk decks → saveOrMergeCards
  │
  ├─ scanned PDF (no text) + Gemini   → send bytes inline, single-shot parse
  ├─ scanned PDF + DeepSeek           → Error ("switch to Gemini")
  │
  └─ text / YouTube-with-hint         → single-shot structured parse
                                        → filter URL-echo cards
                                        → Error if zero real cards (never fabricate)
                                        → saveOrMergeCards
```

`saveOrMergeCards` either creates a new deck or merges into an existing one
(fuzzy front-text match; new translations appended to `notes`). **Interim
behaviour** — PLAN.md D5 wants this explicit + reversible with a preview.

## SRS (`domain/SrsScheduler`)

Pure `next(State, Grade) → State`. `Grade` maps from the UI's confidence buttons:
`LOW→AGAIN`, `MEDIUM→GOOD`, `HIGH→EASY`.

- AGAIN: `repetitions → 0`, interval `→ 1`, ease `−0.25` (floor 1.3)
- GOOD: interval ladder `1 → 3 → round(interval × ease, min 6)`, ease unchanged
- EASY: same ladder × 1.4, ease `+0.15` (ceiling 3.0)

The ViewModel additionally re-orders the *in-session* queue by confidence
(struggled cards resurface near the front). PLAN.md D6: these two mechanisms
should be reconciled into one model.

## Persistence

| Data | Store | Notes |
|---|---|---|
| Decks, flashcards, chat logs | Room (`adaptive_flow_database`) | `exportSchema=true`; migrations required per version |
| AI provider choice, learning goal, TTS prefs | plain `SharedPreferences` (`adaptiveflow_prefs`) | |
| API keys | `EncryptedSharedPreferences` (`adaptiveflow_secure`) | excluded from backup |
| XP, streak, last-study-date, seed flag | plain prefs (interim) | PLAN.md D4: move to Room |

## Known debt (tracked in PLAN.md)

- `MainActivity.kt` ~5.3k lines — split by feature (Phase 2, A1)
- One god `StudyViewModel` — decompose into repositories + per-screen VMs (A2)
- No DI — manual construction (A3)
- No navigation library — `when(tab)` routing, no back stack, no saved state (A4, A5)
- `Int` PKs where Room wants `Long` (D3)
- Design system: hundreds of inline `Color(0x…)` / `.dp`, no `stringResource` (U1)
- Dark mode disabled (U2)
