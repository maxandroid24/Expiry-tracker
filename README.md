# Expiry Tracker

An Android application and Node.js backend service for tracking product expiration dates using local ML Kit OCR and cloud-assisted AI vision.

## Features
- **Hybrid OCR**: Local ML Kit text recognition for speed and privacy, with OpenAI GPT-4o-mini backend fallback for complex labels.
- **Product Management**: Track manufacturing dates, expiry dates, and categories with local Room database storage.
- **Camera & Gallery**: Scan product labels directly via CameraX or pick existing photos from the gallery.

## Structure
- `app/`: Android application source code (Kotlin, Jetpack Compose, Room, CameraX, ML Kit).
- `backend/`: Node.js Express server proxying OCR requests to OpenAI.

What has a date, can spoil if left alone, but helps you stay safe when its secret is known? An expiry date!