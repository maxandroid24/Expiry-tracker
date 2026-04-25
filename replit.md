# Expiry Tracker — Native Android + OCR Backend

## Project type
Two pieces in one repo:

1. **`/app`** — A native Android application (Kotlin, Jetpack Compose, Room,
   CameraX, Google ML Kit). This is **not** a web app and cannot run in the
   Replit preview pane — it must be built and run on a desktop with the
   Android SDK (Android Studio) or on a CI runner.
2. **`/backend`** — A small Node.js/Express service that exposes a single
   `POST /ocr` endpoint. It accepts a base64-encoded image from the Android
   app and proxies it to OpenAI's vision model. **The OpenAI API key lives
   only on this backend** — it is never shipped with the Android app.

## Why a backend?
The original Android app called OpenAI directly using a `BuildConfig`
`OPENAI_API_KEY`. That meant the secret was baked into the APK. The current
architecture removes that completely:

```
Android App  ──base64 image──▶  Backend (/ocr)  ──vision call──▶  OpenAI API
```

- No API key in the APK.
- No `BuildConfig.OPENAI_API_KEY`.
- No hardcoded secrets anywhere in `/app`.
- The Android app only knows the URL of the backend (set via
  `BACKEND_OCR_URL` in `local.properties`).

## Replit environment setup
- **Java toolchain** (GraalVM 19 / OpenJDK) — installed via the
  `java-graalvm22.3` module, used by the Gradle wrapper.
- **Node.js 20** — runs the backend.
- **`OPENAI_API_KEY`** — stored as a Replit Secret, read by the backend at
  startup.
- **Workflow `Start application`** — runs `node backend/server.js` on
  `0.0.0.0:5000`. The web preview shows a backend status page that fetches
  `/health` and lists the API contract.
- The Android SDK is **not** installed in this container — it's ~10 GB and is
  intended for a desktop development setup. Build the APK locally in Android
  Studio.

## Backend API
**`GET /health`** — returns `{ ok, openaiConfigured, model, uptime }`.

**`POST /ocr`** — extracts product info from a label image.

Request:
```json
{
  "image": "<base64-encoded JPEG bytes, <= 8 MB>",
  "hint":  "<optional ML Kit OCR text>"
}
```

Response:
```json
{
  "name":       "Whole Wheat Bread",
  "mfgDate":    "2025-01-12",
  "expDate":    "2025-02-08",
  "confidence": 0.92
}
```

Dates are ISO `YYYY-MM-DD` (or `YYYY-MM` when only month/year is printed).
Any field can be `null`. Rate limited to 20 requests per minute per IP.
Body size capped at ~12 MB JSON (~8 MB of base64).

## Project layout
```
app/                                  # Android app module (Kotlin)
  build.gradle.kts                    # No more OPENAI_API_KEY field — only BACKEND_OCR_URL
  src/main/AndroidManifest.xml
  src/main/java/com/example/expirytracker/
    ExpiryApplication.kt
    MainActivity.kt
    data/                             # Room entity, DAO, database, repository
    network/
      OcrApi.kt                       # Retrofit interface for the backend
      CloudOcrService.kt              # Cloud fallback — calls the backend, NOT OpenAI
    ocr/
      OcrAnalyzer.kt                  # ML Kit on-device text recognition
      TextParser.kt                   # Heuristic name + MFG/EXP date parser
      HybridExtractor.kt              # On-device first → backend fallback
    ui/                               # Compose screens, components, navigation, theme
    utils/                            # Date and image helpers (image compression, base64)
    viewmodel/ProductViewModel.kt     # ViewModel + StateFlow for scan state
backend/                              # Node.js OCR backend
  package.json
  server.js                           # Express server: /health, /ocr (rate limited)
  public/index.html                   # Status page served at /
build.gradle.kts                      # Root Gradle config
settings.gradle.kts
gradle.properties
local.properties.example              # Template — set BACKEND_OCR_URL here (no API keys!)
replit.md
README.md
```

## Build & run the Android app (developer machine)
1. Install **Android Studio Hedgehog or newer** with Android SDK 34.
2. Open this folder in Android Studio (Gradle sync runs automatically).
3. Copy `local.properties.example` → `local.properties` and set
   `BACKEND_OCR_URL` to the URL of your running backend. Examples:
   - Emulator hitting backend on the host machine: `http://10.0.2.2:5000/`
   - Physical device on the same Wi-Fi: `http://192.168.1.42:5000/`
   - Public Replit deployment: `https://<your-repl>.replit.app/`
4. Connect a device or start an emulator and press **Run**.

Command-line build (Android SDK + `ANDROID_HOME` required):
```
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Run the backend locally
```
cd backend
npm install
OPENAI_API_KEY=sk-... node server.js
```

## Deployment
The backend is a standard Express app and can be deployed via Replit's
deployment feature. The Android app is distributed as an APK to devices
through the Play Store, Firebase App Distribution, or sideloading.
