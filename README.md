# Expiry Tracker — Native Android + OCR Backend

A native Android app that lets you track product expiry dates using on-device
OCR, with a server-side OpenAI fallback for messy labels. The architecture is
split into two pieces so that **no API keys ever ship in the APK**:

```
Android App  ──base64 image──▶  Backend (POST /ocr)  ──vision call──▶  OpenAI API
```

## Features

- Add, edit, and delete products (name, type, manufacturing date, expiry
  date, optional image).
- Product list with **green** for valid items, **yellow** for near-expiry,
  and **red** for expired.
- Capture a label using the **back camera** (CameraX) or pick an image from
  the **gallery**.
- **Hybrid text extraction**:
  1. Fast on-device pass with **Google ML Kit** text recognition + a
     heuristic parser that pulls out product name, MFG date, and EXP date.
  2. If the on-device confidence is below the threshold (default `0.6`), the
     app falls back to the **backend `/ocr` endpoint**, which calls OpenAI's
     vision model server-side. Falls back silently if no backend URL is
     configured.
- Auto-fills the Add/Edit product form with extracted values, leaving them
  fully editable before saving.
- Local persistence with **Room** (SQLite). Images are stored in app-private
  files.
- Material 3 + Jetpack Compose UI, Android 7.0+ (minSdk 24, target 34).

## Security model

- **No** OpenAI API key in the APK.
- **No** `BuildConfig.OPENAI_API_KEY`.
- **No** hardcoded secrets in the Android source tree.
- The Android app only knows the URL of the backend (`BACKEND_OCR_URL`,
  configured via `local.properties`).
- The backend reads `OPENAI_API_KEY` from its own environment, validates &
  size-limits the incoming image, and rate-limits per IP.

## Project layout

```
app/                                  # Android app module
  src/main/java/com/example/expirytracker/
    ExpiryApplication.kt              # Application + DB + Repository singletons
    MainActivity.kt                   # Compose host
    data/                             # Room entity, DAO, database, repository
    network/
      OcrApi.kt                       # Retrofit interface for the backend
      CloudOcrService.kt              # Cloud fallback — calls the backend, NOT OpenAI
    ocr/                              # ML Kit recognizer, heuristic parser, hybrid coordinator
    ui/                               # Compose screens, components, navigation, theme
    utils/                            # Date helpers; image compression + base64
    viewmodel/ProductViewModel.kt     # ViewModel + StateFlow for scan state
backend/                              # Node.js / Express OCR backend
  server.js                           # POST /ocr, GET /health, rate limiting, validation
  public/index.html                   # Status page served at /
```

## Backend API

### `GET /health`
```json
{ "ok": true, "openaiConfigured": true, "model": "gpt-4o-mini", "uptime": 12.3 }
```

### `POST /ocr`
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

- Dates are ISO `YYYY-MM-DD` (or `YYYY-MM` when only month/year is printed).
  Any field may be `null` if it cannot be read.
- Rate limited to **20 requests per minute per IP**.
- Body size capped (~12 MB JSON, ~8 MB of base64).

## Run the backend

On Replit, the backend is already wired up to the **Start application**
workflow on port 5000. Set `OPENAI_API_KEY` in Replit Secrets.

Locally:
```bash
cd backend
npm install
OPENAI_API_KEY=sk-... node server.js
# Now serving on http://localhost:5000
```

## Build the Android app

1. Install **Android Studio Hedgehog or newer** with Android SDK 34.
2. Open this folder in Android Studio — Gradle sync runs automatically.
3. Copy `local.properties.example` → `local.properties` and set
   `BACKEND_OCR_URL` to the URL of your running backend. Examples:
   - Android emulator hitting backend on the host machine: `http://10.0.2.2:5000/`
   - Physical device on the same Wi-Fi as the dev backend: `http://192.168.1.42:5000/`
   - Public Replit deployment of the backend: `https://<your-repl>.replit.app/`
4. Connect a device or start an emulator and press **Run**.

From the command line (with Android SDK installed and `ANDROID_HOME` set):
```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

> If you'd rather use a different build, install Gradle 8.7+ (or run
> `gradle wrapper` once inside the project) and run the same command.
