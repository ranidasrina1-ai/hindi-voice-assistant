# Hindi Voice Assistant

Offline-first Hindi, Hinglish and English voice assistant for Android. Tap the mic, speak a command, and the app performs it or asks for confirmation.

Built for Lava Z35 (Android 14, arm64, 3.9 GB RAM), but works on other arm64 Android 8.0+ phones.

Package: `com.voiceassistant.hindi`

## What it does

- Google search from voice
- Call a contact
- Open a WhatsApp chat with a prefilled message and optionally click Send
- Play a song in ArchiveTune (`moe.rukamori.archivetune`)
- Local rule parser first; Qwen2.5-0.5B JSON fallback only when rules fail
- Hindi/Hinglish and English speech models
- Visible Qwen download progress with percentage and MB counter
- On-device transcript log of recent commands, clearable from the app
- Flexible contact phrasing and Hindi-to-Latin contact-name matching
- Tap-to-talk, home screen shortcut, and Quick Settings tile
- Confirmation screen before calls and WhatsApp messages
- No account, no cloud speech API

## Commands

```text
Google par weather search karo
Weather search karo
Search cricket score on Google
Amit Kumar ko call karo
Priya se baat karo
Call Rahul
Rahul ko phone lagao
Sneha ko message bhejo ki kal milte hain
Rahul Sharma ko WhatsApp par message bhejo ki main ghar aa raha hoon
WhatsApp par Rahul ko message bhejo ki hello
ArchiveTune par Kesariya play karo
Kesariya chalao
गूगल पर मौसम खोजो
मौसम बताओ
राहुल को कॉल करो
राहुल को फ़ोन करो
राहुल को मेसेज भेजो कि कल मिलते हैं
व्हाट्सएप पर राहुल को मैसेज भेजो कि नमस्ते
```

Contact names are matched from the phone's contacts. Hindi/Devanagari names are transliterated so they can match Latin-saved contacts, with fuzzy matching for small recognition errors. A spoken number can also be used. Calls and WhatsApp always require confirmation.

## How it works

1. `AssistantService` starts as a microphone foreground service from the visible app.
2. Vosk transcribes a short utterance with the Hindi or English Indian small model.
3. `CommandParser` tries deterministic Hindi/Hinglish/English patterns.
4. If the rules fail and the Qwen model is installed, `QwenEngine` runs llama.cpp with a JSON grammar.
5. Search/music intents use Android intents. Call/WhatsApp intents wait for confirmation.
6. WhatsApp auto-send uses a restricted AccessibilityService that only clicks the send button. It does not store, log, or transmit chat content.

## Models and size

| Item | Size | When |
|---|---|---|
| Vosk Hindi small `vosk-model-small-hi-0.22` | ~42 MB zip | Bundled in APK assets, extracted on first use |
| Vosk English Indian small `vosk-model-small-en-in-0.4` | ~36 MB zip | Bundled in APK assets, extracted on first use |
| Qwen2.5-0.5B-Instruct Q4_0 GGUF | 428,730,208 bytes | Downloaded once over WiFi from Hugging Face |
| Expected APK | ~95–110 MB | GitHub Actions artifact |
| Expected installed data | ~600–650 MB | APK + Vosk models + Qwen |

The Qwen file is checked against SHA-256:

```text
7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed
```

Model source:

- Vosk models: https://alphacephei.com/vosk/models
- Qwen GGUF: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF

## Build with GitHub Actions

The app is built in the cloud because Termux does not have a full Android build toolchain. No laptop is required.

### 1. Create the repository

Create a new public GitHub repository named `hindi-voice-assistant`, then push this folder:

```bash
cd hindi-voice-assistant
git init
git add .
git commit -m "Initial Hindi voice assistant"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/hindi-voice-assistant.git
git push -u origin main
```

The Vosk zips and llama.cpp source are not committed. The workflow downloads them during the build.

### 2. Generate the signing key (one time)

In Termux:

```bash
pkg install openssl
bash scripts/generate-keystore.sh
```

This creates:

```text
keystore/release.p12       offline backup
keystore/release.p12.b64   paste into a GitHub secret
secrets.env                do not commit
keystore-password.txt      offline backup
```

Keep the `.p12` file and passwords safe. If the key is lost, Android will refuse to install future updates over the old app; you would have to uninstall and reinstall.

### 3. Add GitHub Actions secrets

Repository -> Settings -> Secrets and variables -> Actions -> New repository secret:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | entire contents of `keystore/release.p12.b64` |
| `KEYSTORE_PASSWORD` | from `secrets.env` |
| `KEY_ALIAS` | `voiceassistant` |
| `KEY_PASSWORD` | same as `KEYSTORE_PASSWORD` |

### 4. Run the build

Actions -> **Build release APK** -> **Run workflow**.

When it finishes, download `HindiVoiceAssistant-v1.0.1.apk` from the workflow artifact. For a GitHub Release, push a version tag:

```bash
git tag v1.0.1
git push origin v1.0.1
```

## Install on the phone

1. Download the APK and allow "Install unknown apps" for the browser or file manager.
2. Open the app and grant Microphone. Notifications are recommended; on Android 13+ the ongoing service notification is hidden if you deny it, but commands still work.
3. Grant Contacts and Phone permissions from the Setup card.
4. Open the Setup card -> **WhatsApp auto-send** -> **Accessibility kholo**.
5. If Android says the setting is restricted:
   - App info -> three-dot menu -> **Allow restricted settings**
   - Accessibility -> **WhatsApp Send Button** -> On
   This is an Android sideload protection. The app cannot and does not bypass it.
6. Optional: add the **Voice Assistant** Quick Settings tile, or use the launcher long-press shortcut **Command bolo**.
7. Download the Qwen model over WiFi and keep the app open until verification finishes. The download resumes across app restarts through Android DownloadManager.

Android 14 requires the microphone foreground service to start from a visible app, so the tile and shortcut open the app before listening.

## Privacy and security

- Speech recognition runs locally with Vosk.
- The fallback model runs locally with llama.cpp.
- The only network use is the one-time Qwen model download.
- Contacts are read on-device for name lookup and are not stored or uploaded.
- The AccessibilityService is limited to `com.whatsapp` and `com.whatsapp.w4b` in its config.
- It traverses the current WhatsApp window only to find and click the send button. It does not store, log, or transmit screen content.
- App backups are disabled.
- The last 20 recognized commands are stored locally on the device so they can be reviewed in the app. They are never uploaded, and **Clear** removes them.

## Architecture

```text
MainActivity / Compose UI
  -> AssistantState (in-process StateFlow)
  -> AssistantService (microphone FGS)
       -> ModelInstaller (Vosk assets)
       -> Vosk Recognizer
       -> CommandParser
       -> QwenEngine -> QwenNative -> llama.cpp JNI
       -> ActionExecutor
            -> Google search intent
            -> call intent
            -> WhatsApp intent + AccessibilityService send click
            -> ArchiveTune MEDIA_PLAY_FROM_SEARCH intent
  -> AssistantLog (recent commands, local only)
  -> QwenModelManager (DownloadManager + SHA-256)
```

Key files:

```text
app/src/main/java/com/voiceassistant/hindi/AssistantService.kt
app/src/main/java/com/voiceassistant/hindi/CommandParser.kt
app/src/main/java/com/voiceassistant/hindi/ActionExecutor.kt
app/src/main/java/com/voiceassistant/hindi/QwenEngine.kt
app/src/main/cpp/llama_jni.cpp
.github/workflows/build-release.yml
```

## Local development

Requirements: JDK 17, Android SDK platform 34, build-tools 34.0.0, NDK 26.1.10909125, CMake 3.22.1.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Download the Vosk zips into `app/src/main/assets/vosk/` if you want a runtime-testable APK outside CI. The Gradle task `fetchLlamaCpp` downloads llama.cpp v0.7.0 automatically before the native build.

## Troubleshooting

**"Kuch sunai nahi diya"**
Check microphone permission, speak within 8 seconds of tapping, and keep the phone mic unobstructed.

**WhatsApp opens but Send is not clicked**
Enable Accessibility for **WhatsApp Send Button**. If the option is locked, allow restricted settings from App info. You can always press Send manually.

**ArchiveTune not found**
The official package is `moe.rukamori.archivetune`. Other forks use different package names.

**Qwen download stays paused**
It is WiFi-only by design. Connect to WiFi; Android DownloadManager resumes automatically. Keep at least 500 MB free.

**Qwen download finished but fallback still unavailable**
Open the app and let the "Verify ho raha hai" step finish. A failed hash deletes the file and asks for a fresh download.

**Build fails at CMake/NDK**
Make sure the workflow installed NDK `26.1.10909125` and CMake `3.22.1`. The first build downloads llama.cpp and compiles it for arm64 only.

## Known limits

- No always-on hotword; every command starts from the mic, tile, or shortcut.
- Hindi and English models are small; noisy environments reduce accuracy.
- The Qwen fallback takes roughly 10–25 seconds on this class of phone.
- Ambiguous contact names resolve to the best match; the confirmation screen shows the number before calling or messaging.
- WhatsApp auto-send checks the open chat title first. If it cannot verify the chat, it leaves Send to you instead of clicking blindly.
- Dual-SIM calls use the phone's default SIM.
- The app is intended for personal sideloading, not Play Store distribution.

## Licenses

- Vosk and the Vosk models: Apache License 2.0 (see the model archives for their own notices).
- llama.cpp: MIT License.
- Qwen2.5-0.5B-Instruct: Apache License 2.0 (see the Hugging Face model card).
- ArchiveTune is a separate application maintained by its authors.
