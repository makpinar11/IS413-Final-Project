# IS413 Final Project - MNIST PIN Authentication

An Android app that uses a machine learning model to authenticate users with a hand-drawn 4-digit PIN.

## What It Does

Instead of typing a PIN, you draw digits (0-9) on the screen. The app uses a trained MNIST model to recognize what you drew and verify your identity.

## Features

- Draw 4 digits to create your PIN
- Confirm each digit as you draw it
- 3 failed attempts = 15 second lockout
- Reset PIN button to start over
- UMBC colors (gold and black)

## How to Run

1. Clone this repo
2. Open in Android Studio
3. Run on Android device or emulator (API 24+)

## Tech Used

- Java
- TensorFlow Lite (MNIST model)
- FingerPaintView library
- SharedPreferences for storage

## Course Info

- **Course**: IS413 - Mobile App Development
- **School**: UMBC
- **Semester**: Fall 2025
