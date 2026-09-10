# Real native-quality speech — options

**Problem:** the system TTS engines AdaptiveFlow relies on today are mediocre.
Google's Swedish (`sv-SE`) local voice is old and robotic; its English local
voices are only adequate; anything relying on the network voices needs a
connection and still isn't great. For a *language-learning* app, where the point
is to hear a word pronounced well, this is a real gap.

This doc lays out the routes to fix it. Nothing here is implemented yet.

---

## The landscape

High-quality TTS needs **either** a sizeable neural model on the device
(~30–300 MB) **or** a cloud call. There is no small, offline, high-quality
option — the system engines are small and offline, which is exactly why they
sound the way they do.

### On-device neural (offline, free, private)

| Engine / model | Coverage | Size | Licence | Notes |
|---|---|---|---|---|
| **Sherpa-ONNX + Piper voices** | ~50 languages, 900+ voices incl. multiple English + `sv_SE-nst-medium` | ~5–15 MB native libs + ~20–70 MB per voice | Apache-2.0 / MIT | The pragmatic multilingual choice. This is the stack **AvaCore** already uses. Streams first audio quickly. |
| **Kokoro-82M** (via Sherpa-ONNX) | English (many accents) excellent; ja/zh/fr/hi/it/pt community | one ~80–330 MB model | Apache-2.0 | Best English quality in the open-weights world; narrow language set. |
| **MMS-TTS** (Meta) | 1000+ languages | ~40 MB per language | **CC-BY-NC 4.0** ⚠️ | Non-commercial licence — a blocker for a shipped app. Quality varies. |
| **Coqui XTTS-v2** | 17 languages, voice cloning | ~1.8 GB | **CPML** ⚠️ non-commercial; Coqui defunct | Impractical on a phone and licence-blocked. |

### Cloud neural (online, best quality, costs money / a key)

| Service | Swedish | English | Pricing (rough) | Fit for a BYO-key app |
|---|---|---|---|---|
| **Gemini TTS** (`gemini-2.5-flash-preview-tts`) | ✅ (24 langs, style-promptable) | ✅ | audio output tokens on the **key the user already has** | **Best fit** — no new key, no new service; import text already goes to Gemini. |
| **Azure Speech** neural | ✅ `sv-SE-SofieNeural` / `MattiasNeural` — genuinely good | ✅ huge set | 500 K chars/mo free, then ~$16/1M | Needs a second key + endpoint region. |
| **Google Cloud TTS** (Neural2 / Chirp3-HD) | ✅ Wavenet decent; Chirp3-HD limited langs | ✅ | 1 M WaveNet chars/mo free, then ~$4–30/1M | Different key/project from the Gemini AI-Studio key. |
| **Amazon Polly** neural | ✅ `Elin` (newer) | ✅ | ~$16/1M neural | Another key + AWS setup. |
| **OpenAI** (`gpt-4o-mini-tts`) | ⚠️ ok, English-leaning | ✅ | ~$0.015/1M input chars — very cheap | Another key. |
| **ElevenLabs** (multilingual v2 / flash v2.5) | ✅ (29–32 langs) | ✅ best expressiveness | subscription, ~$0.15–0.30/1K chars | Another key; priciest; overkill for word-level playback. |

---

## Recommended plan for AdaptiveFlow

A three-tier strategy that keeps the app's offline-first, BYO-key, no-backend
character.

### Tier 1 — default: on-device Piper via Sherpa-ONNX  *(the main piece, ~2–4 days)*

- Add the `sherpa-onnx` Android AAR (JNI + ONNX Runtime).
- A `VoiceModelManager`: on first use of a language, download that Piper voice
  (~30–60 MB) from the piper-voices repository into `filesDir`, versioned and
  copied atomically; cache thereafter. Nothing ships in the APK.
- Route `TtsController.speak()` through a new `NeuralTtsEngine` when a model for
  the current locale is present; fall back to the system engine otherwise.
- Result: consistent, natural voices for **every** language the app supports,
  offline, free, private. Fixes the "Google Swedish is robotic" problem at the
  root.

### Tier 2 — opt-in premium: "Natural AI voice" on the existing Gemini key  *(~1 day on top of Tier 1)*

- A `GeminiTtsProvider` behind the same `AiProvider`-style seam.
- A toggle in TTS settings. When on and online and a Gemini key is set, synth
  goes to `gemini-2.5-flash-preview-tts`; otherwise it silently falls back to
  Tier 1.
- Zero new keys, zero new services — the text already goes to Gemini for import.

### Tier 3 — Persian specialist: AvaCore  *(~1 day, independent)*

- `/home/opsquad/Workspace/AvaCore` — the user's standalone offline Persian TTS
  engine (Piper VITS + eSpeak-NG G2P + a real Persian NLP front-end: ezafe,
  number expansion, normalisation). It registers as a system
  `TextToSpeechService`, so any app can use it.
- Its Persian NLP front-end beats a generic Piper Persian voice, so for `fa` it's
  worth preferring over Tier 1.
- Integration: keep two `TextToSpeech` instances — the default and an AvaCore one
  (only if `com.github.opscalehub.avacore` is in `TextToSpeech.getEngines()`) —
  and route `speak()` to AvaCore when `locale.language == "fa"`.
- Caveats: AvaCore isn't on Play Store (63 MB model via `download_assets.sh`), so
  AdaptiveFlow can't auto-install it — detect its absence and nudge via the
  existing `missingLanguage` / `ACTION_INSTALL_TTS_DATA` hook. Its package name
  may change if the project migrates orgs (discover via `getEngines()` rather
  than hardcode). Persian-only, so it's strictly additive.

### Fallback

The current system-TTS path stays as the last resort (no model downloaded, no
key, offline, no AvaCore).

---

## Priority

1. **Tier 1 (Piper / Sherpa-ONNX)** — the real fix, matches the app's ethos, no
   recurring cost. Do this first.
2. **Tier 3 (AvaCore)** — small, high value for the primary user, independent of
   Tier 1.
3. **Tier 2 (Gemini TTS)** — nice premium option once Tier 1's plumbing exists.
