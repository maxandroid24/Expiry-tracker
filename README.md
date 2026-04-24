# Expiry Tracker — Native Android (Kotlin)

A native Android app that lets you track product expiry dates with on-device OCR
and an optional cloud AI fallback for messy labels.

## Features

- Add, edit, and delete products (name, type, manufacturing date, expiry date, optional image)
- Product list with **green** badge for valid items and **red** for expired ones
- Capture a label using the **back camera** (CameraX) or pick an image from the **gallery**
- **Hybrid text extraction**:
  1. Fast on-device pass with **Google ML Kit** text recognition + a heuristic parser that pulls out product name, MFG date, and EXP date.
  2. If the on-device confidence is below the threshold (default `0.6`), the app falls back to a cloud vision LLM (OpenAI `gpt-4o-mini`) for a higher-quality structured extraction. Falls back silently if no API key is configured.
- Auto-fills the Add/Edit product form with extracted values, leaving them fully editable before saving
- Local persistence with **Room** (SQLite). Images are stored in app-private files.
- Material 3 + Jetpack Compose UI, Android 7.0+ (minSdk 24, target 34)

## Project structure

```
app/src/main/java/com/example/expirytracker/
├── ExpiryApplication.kt      # Application + DB + Repository singletons
├── MainActivity.kt           # Compose host
├── data/                     # Room entity, DAO, database, repository
├── network/CloudOcrService.kt# OpenAI vision fallback (optional)
├── ocr/                      # ML Kit recognizer, heuristic parser, hybrid coordinator
├── ui/
│   ├── components/           # ProductCard
│   ├── navigation/           # Nav graph
│   ├── screens/              # List, Add/Edit, Camera screens
│   └── theme/                # Material 3 theme
├── utils/                    # Date and image helpers
└── viewmodel/ProductViewModel.kt
```

## Build & run

1. Install **Android Studio Hedgehog or newer** with the Android SDK 34.
2. Open this folder (`android-expiry-tracker`) in Android Studio. It will sync Gradle automatically.
3. (Optional) For the cloud OCR fallback:
   - Copy `local.properties.example` → `local.properties` (Android Studio creates `local.properties` on first sync; just append the line below).
   - Add your OpenAI API key:
     ```
     OPENAI_API_KEY=sk-...
     ```
   - The key is read at build time and exposed via `BuildConfig.OPENAI_API_KEY`. If left blank, the app uses on-device OCR only.
4. Connect a device or start an emulator and press **Run**.

> If you'd rather build from the command line, install Gradle 8.7+ (or run `gradle wrapper` once inside the project), then `./gradlew assembleDebug`.

## How the hybrid extractor works

`HybridExtractor.extract(bitmap)`:

1. Runs **ML Kit** Latin text recognition on-device (`OcrAnalyzer`).
2. Feeds the raw text into `TextParser`, which uses regexes + keyword lookups
   (`MFG`, `EXP`, `BEST BEFORE`, etc.) to extract product name and dates and
   compute a `0..1` confidence score.
3. If the score is below the threshold *and* `OPENAI_API_KEY` is configured, it
   re-asks `gpt-4o-mini` with the image (and the on-device text as a hint),
   demanding strict JSON. If the cloud result is more confident, it wins.
4. The form pre-fills with the result; the user can correct any field before saving.

## Permissions

Declared in the manifest:

- `CAMERA` — required for the in-app camera capture.
- `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` (older) — for the gallery picker.
- `INTERNET` — only used when the cloud OCR fallback is configured.

## Notes

- All product images are stored under `filesDir/product_images/` and are
  removed when the corresponding product is deleted.
- Dates are stored as epoch millis at UTC midnight to keep comparisons stable
  across time zones.
- The app uses dynamic Material 3 theming on Android 12+ and falls back to a
  custom green palette on older devices.
