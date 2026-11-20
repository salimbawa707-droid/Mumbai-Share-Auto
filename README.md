# Mumbai Share Auto — Native Android Application (Phase 1 Meter-Reuse Integrated)

## Overview
Mumbai Share Auto is a separate, independent native Android application developed with **Kotlin**, **Jetpack Compose**, and **Material 3** for shared-auto route coordination and total booking fare agreements in Mumbai, India.

## Package & App Details
- **Application ID / Package**: `com.mumbaishareauto.app`
- **Application Name**: Mumbai Share Auto
- **Compile SDK**: 36
- **Target SDK**: 36
- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Toolchain Requirements**:
  - JDK: Java 17
  - Android Gradle Plugin: 8.8.0
  - Kotlin: 2.0.21
  - Kotlin Compose Compiler Plugin: 2.0.21
  - Jetpack Compose BOM: 2024.10.01
  - Room SQLite: 2.7.1

## Reusable Components Integrated from Mumbai Auto Meter
1. **Designated Mumbai Station Stand Directory (`MumbaiStationDirectory.kt`)**:
   - Comprehensive reference directory of physical railway station share-auto stands across **Western**, **Central**, and **Harbour** lines (Bandra, Andheri, Santacruz, Vile Parle, Goregaon, Malad, Borivali, Dadar, Kurla, Ghatkopar, Kanjurmarg, Thane, Chembur, Vashi).
   - Landmarks, station exit sides (East/West), via points, distances, and GPS coordinates.
   - **Crucial Adaptation**: Historical fares from the meter project are treated purely as indicative reference guides; they are *never* used as per-seat fare multipliers.
2. **Local Favorite Stands Storage (`FavoriteRouteRepository.kt` & `FavoriteRouteEntity.kt`)**:
   - Allows users to bookmark frequent station stands locally on their device without pretending it is an online network database.
3. **Responsive Material 3 UI & Real Semantics**:
   - Railway line filter chips (`All Lines`, `Western`, `Central`, `Harbour`).
   - Search bar with instant station, stand, and destination filtering.
   - Real Jetpack Compose accessibility semantics (`Modifier.semantics { contentDescription = "..." }`).
   - Quick action to pre-fill passenger route offers from station stands.
   - External Google Maps stand location launcher (`geo:lat,lng` intent).
   - Stand sharing helper via system share sheet.

## Excluded Meter Project Functionality
- Electronic meter tariff engine (`AutoMeterEngine.kt`, `MumbaiAutoFareEngine.kt`, `TariffConfig.kt`).
- GPS distance metering, waiting-time accumulator, and speed smoothing filters.
- Foreground tracking services and floating mini-meter window.
- Trip history and document reminder system (`TripEntity`, `DocumentEntity`).
- QR code generation, UPI payment handlers, and Paytm/PhonePe soundbox voice engine.
- Fake hardcoded active drivers, fake phone numbers, and mock booking confirmations.

## Locked Version 1 Business Rules
1. **Total Fare per Booking**: Driver posts total fare for the booking, not a per-seat rate.
2. **One Booking = One Passenger Party**: Different passenger parties are not automatically merged.
3. **Private Split**: Passengers privately arrange cost sharing; the app does not force or track individual contributions.
4. **Designated Pickup Point**: Driver waits at the designated station pickup point; passengers travel there.
5. **Configurable Vehicle Capacity**: Capacity is validated against configurable RTO limits (default 3 passengers) rather than hardcoded assumptions.
6. **Strict Lifecycle Transitions**: Fares cannot be locked without driver acceptance and passenger confirmation.
7. **Honest Status**: No simulated payment success, mock backend, or fabricated driver availability.
