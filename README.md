# Mumbai Share Auto — Native Android Application

## Overview
Mumbai Share Auto is a separate, independent native Android application developed with **Kotlin**, **Jetpack Compose**, and **Material 3** for shared-auto route coordination and total booking fare agreements in Mumbai, India.

## Package & App Details
- **Application ID / Package**: `com.mumbaishareauto.app`
- **Application Name**: Mumbai Share Auto
- **Compile SDK**: 35
- **Target SDK**: 35
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Toolchain Requirements**:
  - JDK: Java 17
  - Android Gradle Plugin: 8.8.0
  - Gradle wrapper: 8.10.2
  - Kotlin: 2.0.21
  - Kotlin Compose Compiler Plugin: 2.0.21
  - Jetpack Compose BOM: 2024.10.01
  - Room SQLite: 2.7.1

> SDK note: Android Gradle Plugin 8.8 supports up to API 35. Keep the configured SDK values at 35 unless the Android Gradle Plugin is upgraded to a version that supports a newer API level.

## Reusable Components Integrated from Mumbai Auto Meter
1. **Designated Mumbai Station Stand Directory (`MumbaiStationDirectory.kt`)**:
   - Reference directory of physical railway station share-auto stands across **Western**, **Central**, and **Harbour** lines (Bandra, Andheri, Santacruz, Vile Parle, Goregaon, Malad, Borivali, Dadar, Kurla, Ghatkopar, Kanjurmarg, Thane, Chembur, Vashi).
   - Landmarks, station exit sides (East/West), via points, distances, and GPS coordinates.
   - Historical fares from the meter project are indicative reference guides only; they are never used as per-seat fare multipliers.
2. **Local Favorite Stands Storage (`FavoriteRouteRepository.kt` & `FavoriteRouteEntity.kt`)**:
   - Allows users to bookmark frequent station stands locally on their device without pretending it is an online network database.
3. **Responsive Material 3 UI & Real Semantics**:
   - Railway line filter chips (`All Lines`, `Western`, `Central`, `Harbour`).
   - Search bar with instant station, stand, and destination filtering.
   - Jetpack Compose accessibility semantics.
   - Quick action to pre-fill passenger route offers from station stands.
   - External Google Maps stand location launcher (`geo:lat,lng`) and system share sheet.

## Excluded Meter Project Functionality
- Electronic meter tariff engine (`AutoMeterEngine.kt`, `MumbaiAutoFareEngine.kt`, `TariffConfig.kt`).
- GPS distance metering, waiting-time accumulator, and speed smoothing filters.
- Foreground tracking services and floating mini-meter window.
- Trip history and document reminder system.
- QR code generation, UPI payment handlers, and payment soundbox voice engine.

## Locked Version 1 Business Rules
1. **Total Fare per Booking**: Driver posts total fare for the booking, not a per-seat rate.
2. **One Booking = One Passenger Party**: Different passenger parties are not automatically merged.
3. **Private Split**: Passengers privately arrange cost sharing; the app does not force or track individual contributions.
4. **Designated Pickup Point**: Driver waits at the designated station pickup point; passengers travel there.
5. **Configurable Vehicle Capacity**: Capacity is validated against configured limits; legal capacity must be verified with the relevant transport authority before production use.
6. **Strict Lifecycle Transitions**: Fares cannot be locked without driver acceptance and passenger confirmation.
7. **Honest Status**: No simulated payment success, mock backend, or fabricated driver availability.

## Build & Verify
Run from the repository root:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The GitHub Actions Android workflow runs from the same root directory and uploads APKs, reports, and test results.

## Firebase setup and Firestore rules
1. Register Android package `com.mumbaishareauto.app` in Firebase and place the downloaded `google-services.json` in `app/google-services.json`.
2. Enable Phone sign-in in Firebase Authentication and configure the SMS region policy for India. Add the debug/release SHA-1 and SHA-256 fingerprints in Firebase Project Settings.
3. Create the default Cloud Firestore database in the Mumbai region (`asia-south1`) when available.
4. Install Firebase CLI, sign in with the Firebase account, then run from the repository root:

```bash
firebase deploy --only firestore:rules
```

The repository includes `firebase.json` and `firestore.rules` for this deployment. Keep Firestore in production mode; do not replace these rules with public test-mode rules. Phone OTP SMS may require a linked Cloud Billing account and can incur charges.
