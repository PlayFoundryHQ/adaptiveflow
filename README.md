# AdaptiveFlow

Adaptive, icon-first **language flashcards** for Android.

Import anything — a paste of text, a YouTube link, a PDF, a `.txt`/`.csv` — and
an AI provider turns it into a study deck. Review with spaced repetition that
adapts to how you're doing, ask an AI tutor when you're stuck, and keep a streak
going on the Quest path.

> **Status: pre-1.0, actively being cleaned up.** See [`PLAN.md`](PLAN.md) for the
> full audit and roadmap and [`docs/architecture/overview.md`](docs/architecture/overview.md)
> for how it's built.

## AI providers

AdaptiveFlow is **bring-your-own-key**. Nothing is bundled or proxied. In
**Settings → AI provider & key** pick one and paste its key:

| Provider | Get a key | Notes |
|---|---|---|
| Google Gemini | <https://aistudio.google.com/apikey> | Can also read scanned/image PDFs |
| DeepSeek | <https://platform.deepseek.com/api_keys> | Text-based sources only |

Keys are stored in `EncryptedSharedPreferences` on-device and are excluded from
cloud backup. Without a key, AI import and the tutor are off — you can still
import a plain `word: meaning` list offline.

### What leaves the device

When a key is set, AdaptiveFlow sends **only** the content you actively import
(pasted text, PDF/file text, the topic hint) and your **tutor chat messages**
directly to the chosen provider over HTTPS, for that request. It goes under
[Google's](https://ai.google.dev/gemini-api/terms) /
[DeepSeek's](https://platform.deepseek.com/) API terms. AdaptiveFlow has no
backend — nothing is sent anywhere else, and there is no analytics or
telemetry. Decks, cards, SRS progress and chat history stay in the local Room
database (which Android's own auto-backup may sync to the user's Google account
unless they disable it).

## Build

**Requirements:** JDK 17, Android SDK (`compileSdk 36.1`).

```bash
# one-time: point Gradle at your SDK
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug testDebugUnitTest
```

Debug builds need no keystore. Release signing is CI-only (env-var injected).

## CI / releases

One workflow, [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

- **PR / push** → lint + unit tests + debug APK
- **push to `main`** (app changes only) → also builds a **signed** release APK and
  publishes it to the GitHub Release for the current `versionName`.

The newest `main` is always installable from
`releases/latest/download/adaptiveflow-latest.apk`.

Cut a release by bumping `versionName` + `versionCode` in
[`app/build.gradle.kts`](app/build.gradle.kts) in a PR.

## Licence

Code: see repository. Third-party: PdfBox-Android (Apache-2.0), AndroidX,
Retrofit/OkHttp/Moshi (Apache-2.0).
