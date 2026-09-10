# AdaptiveFlow

Adaptive, icon-first **language flashcards** for Android.

Import anything — a paste of text, a YouTube link, a PDF, a `.txt` / `.csv` /
`.json` — and an AI provider you choose turns it into a study deck. Review with
spaced repetition that adapts to how you're doing, ask an AI tutor when you're
stuck, and keep a streak going on the Quest path.

**Current release:** `v0.7.1` · min SDK 24 · Kotlin + Jetpack Compose,
single-Activity, `navigation-compose`, manual DI.

Latest build: `releases/latest/download/adaptiveflow-latest.apk`

---

## What it does

| | |
|---|---|
| **Import → deck** | Paste text or a `word: meaning` list, load a PDF / TXT / CSV / JSON, or paste a YouTube link + a topic hint. Large PDFs are chunked and parsed page-by-page with automatic retry/back-off. A pasted or loaded deck **JSON** re-imports offline with no AI call. |
| **Honest offline path** | With no key, a plain `word: meaning` / `word - meaning` / `word = meaning` list still imports. Import **never fabricates** — if AI parsing fails it fails loudly, it never inserts canned cards and reports success. |
| **Adaptive study** | A pure SM-2-style `SrsScheduler`. Struggle on a card and the session resurfaces it and can switch it to multiple-choice; the tutor gets gentler. |
| **AI tutor** | A chat sheet on the study screen — pronunciation, usage examples, simpler explanations, in whatever language you type. |
| **Learner profile** | One setting: **my language** + **what I'm learning**, picked from a curated list (or free-text for anything not listed). Every new deck, quiz and tutor reply is tuned to it. |
| **Master Vocabulary Pool** | A single protected deck everything can merge into, with a **preview** (new / enriched / skipped) and an **undo**. |
| **Export** | Any deck → JSON (re-imports for free) or CSV (opens in Anki / spreadsheets). Building decks costs API tokens; keep the results. |
| **Gamification** | XP, day streak, mastery crowns, weekly quests — all in Room. |
| **Native TTS** | Prefers Google's neural voices, picks the best installed voice for the target language, and nudges you to install a missing language pack. (Voice quality is a known weak spot — see [`docs/tts-options.md`](docs/tts-options.md).) |

## Languages

The **UI ships in English and Persian (فارسی)**. Picking Persian as *my language*
switches the whole interface to Persian, right-to-left included. Any other choice
keeps the English UI while still tuning the AI to that language. Adding another
locale is a `res/values-<code>/strings.xml` file plus one line in
`domain/Languages.kt` — see [`docs/architecture/overview.md`](docs/architecture/overview.md#localisation).

## AI providers

AdaptiveFlow is **bring-your-own-key**. Nothing is bundled or proxied. In
**Settings → AI provider & key** pick one and paste its key:

| Provider | Get a key | Notes |
|---|---|---|
| Google Gemini | <https://aistudio.google.com/apikey> | Can also read scanned / image-only PDFs |
| DeepSeek | <https://platform.deepseek.com/api_keys> | Text-based sources only |

Keys live in `EncryptedSharedPreferences` on-device and are excluded from cloud
backup. Without a key, AI import and the tutor are off.

### What leaves the device

When a key is set, AdaptiveFlow sends **only** the content you actively import
(pasted text, file text, the topic hint) and your **tutor messages** directly to
the chosen provider over HTTPS, for that request, under
[Google's](https://ai.google.dev/gemini-api/terms) /
[DeepSeek's](https://platform.deepseek.com/) API terms. There is **no backend**,
no analytics, no telemetry. Decks, cards, SRS progress and chat history stay in
the local Room database (which Android's own auto-backup may sync to the user's
Google account unless they disable it).

## Build

**Requirements:** JDK 17, Android SDK with `compileSdk 36.1`.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # one-time

./gradlew assembleDebug              # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug testDebugUnitTest # the CI gate
```

Debug builds need no keystore. Release signing is CI-only (env-var injected).

**Visual-regression suite:** `./gradlew recordRoborazziDebug` writes reference
screenshots under `app/src/test/screenshots/`; `verifyRoborazziDebug` diffs them.
Plain `testDebugUnitTest` just renders each screenshot composable as a crash
smoke-test — it never writes or diffs PNGs, so CI has no font-hinting flake.

Bleeding-edge toolchain by choice: **AGP 9.1.1**, `compileSdk 36.1`. If a runner
update breaks the build, that is the first suspect.

## CI / releases

One workflow, [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

- **PR / push** → `lintDebug testDebugUnitTest assembleDebug` (one Gradle
  invocation). `docs/` changes are path-filtered out.
- **push to `main`** (app changes only) → also builds a **signed** release APK
  and publishes / updates the GitHub Release for the current `versionName`.

Cut a release by bumping `versionName` + `versionCode` in
[`app/build.gradle.kts`](app/build.gradle.kts) in a PR.

## Docs

| | |
|---|---|
| [`docs/architecture/overview.md`](docs/architecture/overview.md) | How it's built today — layers, the AI abstraction, import pipeline, SRS, persistence, localisation, TTS. |
| [`CLAUDE.md`](CLAUDE.md) | The invariants that must not regress. |
| [`PLAN.md`](PLAN.md) | The original end-to-end audit (historical) + what's parked + the TTS roadmap. |
| [`docs/tts-options.md`](docs/tts-options.md) | Options for real native-quality speech. |

## Licence

Code: see repository. Third-party: PdfBox-Android (Apache-2.0), AndroidX,
Retrofit / OkHttp / Moshi (Apache-2.0), Roborazzi / Robolectric (Apache-2.0).
