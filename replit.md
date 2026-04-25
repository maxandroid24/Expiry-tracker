# Expiry Tracker (Native Android)

## Project type
Native Android application written in Kotlin using Jetpack Compose, Room, CameraX,
and Google ML Kit. **This is not a web app** — it cannot run in the Replit
preview pane and must be built/run on a desktop with the Android SDK
(Android Studio) or on a CI runner that has the Android SDK installed.

## Replit environment setup
Because there is no web frontend to render, the Replit container has been
configured with the minimum needed to inspect / build the project from the
command line, plus a small static info page so the preview pane shows something
sensible.

- **Java toolchain**: GraalVM 19 / OpenJDK (installed via the `java-graalvm22.3` module).
  Used by the Gradle wrapper.
- **Gradle wrapper**: `./gradlew` (configured in `gradle/wrapper/`). Running an
  actual `./gradlew assembleDebug` requires the **Android SDK 34**, which is
  ~10 GB and is **not** installed in this container.
- **Workflow `Start application`**: runs `jwebserver` on `0.0.0.0:5000`,
  serving `public/index.html` — a static status page describing the project
  and how to build it locally.

## Project layout
```
app/                                  # Android app module
  build.gradle.kts
  src/main/java/com/example/expirytracker/
    ExpiryApplication.kt              # Application + DB + Repository singletons
    MainActivity.kt                   # Compose host
    data/                             # Room entity, DAO, database, repository
    network/CloudOcrService.kt        # OpenAI vision fallback (optional)
    ocr/                              # ML Kit recognizer, heuristic parser, hybrid coordinator
    ui/                               # Compose screens, components, navigation, theme
    utils/                            # Date and image helpers
    viewmodel/ProductViewModel.kt
build.gradle.kts                      # Root Gradle config
settings.gradle.kts
gradle.properties
local.properties.example              # Template — copy to local.properties to set OPENAI_API_KEY
public/index.html                     # Static info page served on port 5000
```

## Build & run (developer machine)
1. Install **Android Studio Hedgehog or newer** with Android SDK 34.
2. Open this folder in Android Studio (Gradle sync runs automatically).
3. Optional: copy `local.properties.example` to `local.properties` and set
   `OPENAI_API_KEY=sk-...` to enable the cloud OCR fallback.
4. Connect a device or start an emulator and press **Run**.

Command-line build (Android SDK + `ANDROID_HOME` required):
```
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Deployment
Not configured. There is nothing meaningful to deploy from Replit for a native
Android project — the build output (`.apk`) is distributed to devices, not
hosted on a web server. APK distribution should be done through the Play Store,
Firebase App Distribution, or another mobile distribution channel.
