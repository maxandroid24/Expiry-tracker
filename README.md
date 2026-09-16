# Expiry Tracker

Expiry Tracker is an Android application with a companion Node.js backend designed to help users track product expiration dates using camera scans, image uploads, and hybrid local/cloud OCR text extraction.

---

## Table of Contents

- [Features](#features)
- [Architecture & Tech Stack](#architecture--tech-stack)
  - [Android App](#android-app)
  - [Backend Service](#backend-service)
- [Hybrid OCR System](#hybrid-ocr-system)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running the Backend](#running-the-backend)
  - [Building & Running the Android App](#building--running-the-android-app)
- [Backend API Endpoints](#backend-api-endpoints)
- [Configuration & Environment Variables](#configuration--environment-variables)

---

## Features

- **Product Expiry Management**: Track product names, manufacturing dates, expiry dates, categories, and custom notes.
- **Status Identifiers**: Color-coded indicators for fresh products, items expiring soon, and expired items.
- **Camera & Gallery Capture**: Capture product labels directly using CameraX or select existing images from your gallery.
- **Hybrid OCR Extraction**: Instant local text recognition via Google ML Kit combined with OpenAI Vision fallback (`gpt-4o-mini`) via the backend server.
- **Offline Persistence**: Products stored locally in an Android Room SQLite database.
- **Scan Logging & Admin Web Dashboard**: Web dashboard hosted by the backend to inspect scan histories, status metrics, and extraction accuracy.

---

## Architecture & Tech Stack

### Android App
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Camera**: CameraX (`androidx.camera`)
- **Local OCR**: Google ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition`)
- **Database**: Room Persistence Library
- **Networking**: Retrofit 2 + OkHttp 4
- **Image Loading**: Coil (`io.coil-kt:coil-compose`)
- **Navigation**: Jetpack Compose Navigation

### Backend Service
- **Runtime**: Node.js (CommonJS)
- **Framework**: Express.js
- **Rate Limiting**: `express-rate-limit`
- **Logging**: `morgan`
- **AI Engine**: OpenAI Chat Completions API (`gpt-4o-mini` default model)

---

## Hybrid OCR System

1. **Local Extraction**: Image captured via CameraX or gallery is processed immediately on-device using ML Kit Text Recognition. Dates (EXP, MFG, Best Before) and product names are parsed using pattern matching (`TextParser.kt`).
2. **Cloud Enhancement / Fallback**: If local confidence is low or additional accuracy is required, the image base64 data is dispatched to the backend proxy service (`/ocr`). The backend calls OpenAI's Vision model with strict JSON formatting to extract product details.
3. **Result Merging**: The app merges local and cloud results (`HybridExtractor.kt`) to ensure maximum accuracy and fast performance.

---

## Project Structure

```
├── app/
│   ├── src/main/java/com/example/expirytracker/
│   │   ├── data/                 # Room entities, DAOs, Database, Repository
│   │   ├── network/              # Retrofit API interface & Cloud OCR service
│   │   ├── ocr/                  # ML Kit analyzer, Regex parsing, Hybrid extractor
│   │   ├── ui/                   # Jetpack Compose UI screens, components, themes
│   │   ├── utils/                # Date and Image processing utilities
│   │   ├── viewmodel/            # ProductViewModel handling business logic
│   │   ├── ExpiryApplication.kt  # Application class
│   │   └── MainActivity.kt       # Activity entry point
│   └── build.gradle.kts
├── backend/
│   ├── public/                   # Static web admin dashboard
│   ├── scanLog.js                # Scan history logging & statistics helper
│   ├── server.js                 # Express server & OpenAI OCR proxy route
│   └── package.json
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Getting Started

### Prerequisites

- **Node.js**: v20 or higher
- **Android Studio**: Ladybug / Jellyfish or newer with JDK 17+
- **Android Device / Emulator**: Running Android 8.0 (API level 26) or higher
- **OpenAI API Key** (optional for cloud vision enhancement)

---

### Running the Backend

1. Navigate to the `backend` directory:
   ```bash
   cd backend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Set your OpenAI API key and start the server:
   ```bash
   export OPENAI_API_KEY="your-openai-api-key"
   npm start
   ```

   The backend runs by default on `http://0.0.0.0:5000`.
   You can view the backend web dashboard by visiting `http://localhost:5000` in your web browser.

---

### Building & Running the Android App

1. Open the root repository folder in **Android Studio**.
2. Ensure local network/backend endpoint configuration in `CloudOcrService.kt` matches your backend IP address (e.g. `http://10.0.2.2:5000/` for standard Android Emulator or your server URL).
3. Sync Gradle projects (`File -> Sync Project with Gradle Files`).
4. Select your connected device or emulator and click **Run** (Shift + F10).

---

## Backend API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Server status check, uptime, and OpenAI configuration state. |
| `POST` | `/ocr` | Accepts JSON `{ image: "<base64>", hint: "..." }`, calls OpenAI Vision, and returns structured product info. |
| `GET` | `/scans` | Returns recent scan logs and statistics (total scans, success rate, latency). |

---

## Configuration & Environment Variables

### Backend Environment Variables

- `PORT`: Server port (default: `5000`).
- `OPENAI_API_KEY`: OpenAI API key for proxy vision requests.
- `OPENAI_MODEL`: Model identifier (default: `gpt-4o-mini`).
