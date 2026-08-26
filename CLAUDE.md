# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Personal Android app (single user: Simon) for tracking camper trips: multi-stop trips with
per-stop nights/camping costs, trip-level expenses (fuel, road tax, other), a fuel-cost
estimator, and MapLibre/OpenStreetMap views of all trips. Kotlin + Jetpack Compose,
single `:app` module. Default currency is CHF (user preference).

**Keep code and docs short and concise.** This matters a lot: minimal prose, no
boilerplate, no restating what the code already says. Prefer editing existing docs over
adding new ones.

## Commands

```bash
./gradlew :app:assembleDebug    # build
./gradlew test                  # unit tests (domain logic only)
./gradlew :app:installDebug     # install on connected device/emulator
./gradlew test --tests "com.nuelto.camperexperience.domain.CostCalculatorTest"  # one test class
```

Emulator workflow (AVD `Pixel_9a` exists locally; needs Play services image for
sign-in/fused location/reverse geocoding — `location/PlaceNameResolver.kt` uses the
platform `Geocoder` and silently returns null without Play services or network):

```bash
~/Android/Sdk/emulator/emulator -avd Pixel_9a -no-snapshot-save &
~/Android/Sdk/platform-tools/adb shell am start -n com.nuelto.camperexperience/.MainActivity
~/Android/Sdk/platform-tools/adb emu geo fix <lon> <lat>   # mock GPS (lon first!)
```

There is no lint/format tooling configured.

## Toolchain constraints (the non-obvious parts)

- **Standalone KGP 2.4.x instead of AGP's built-in Kotlin**: `gradle.properties` sets
  `android.builtInKotlin=false` and `android.newDsl=false` because maplibre-compose 0.15+
  needs Kotlin ≥ 2.4 metadata while every AGP release bundles built-in Kotlin 2.2.10.
  Both opt-outs die in AGP 10 (expected late 2026): when an AGP with built-in Kotlin ≥ 2.4
  exists, remove the two properties and the `org.jetbrains.kotlin.android` plugin.
- **MapLibre must use the OpenGL runtime** (`maplibre-compose-runtime-opengl-android`,
  `runtimeOnly`): the default Vulkan renderer draws a blank map on emulators.
- Map style is OpenFreeMap Liberty (`ui/map/TripMap.kt` → `MAP_STYLE_URL`) — free, no API key.
- Firebase is **conditionally applied**: the google-services plugin only activates if
  `app/google-services.json` exists (it's gitignored). Without it the app builds in
  local-only mode with seeded in-memory data. `webClientId` (Google Sign-In) is read from
  `local.properties`, falling back to `gradle.properties`. Console steps: FIREBASE_SETUP.md.

## Architecture

MVVM + repository, hand-rolled DI — no Hilt, no Room. Package root:
`app/src/main/java/com/nuelto/camperexperience/`.

- **`CamperApp.kt`** holds `AppContainer`, which picks the backing store at startup:
  `FirebaseApp.initializeApp()` returns non-null → Firestore repos + `AuthRepository`;
  null → `InMemoryTripRepository` (seeded demo trips) + in-memory settings. Both sides
  implement the same `TripRepository`/`SettingsRepository` interfaces (`data/`), so
  ViewModels and UI are storage-agnostic. ViewModels get dependencies via
  `containerViewModelFactory { … }` (also in CamperApp.kt) + companion `Factory` objects.
- **Auth gate lives in `MainActivity.AppRoot`**, not in navigation: Firebase mode with no
  signed-in user renders `SignInScreen` instead of the NavHost. All ViewModels can assume
  a signed-in user in Firebase mode.
- **Firestore layout**: `users/{uid}` (settings doc) → `trips/{tripId}` →
  `stops/{stopId}`, `expenses/{expenseId}`. Reads are snapshot-listener `callbackFlow`s;
  writes are fire-and-forget so they queue offline (never `await()` a write — it would
  block until server ack). Models use `LocalDate`, stored as epoch-day Longs; mapping is
  manual (no Firestore reflection) in `FirestoreTripRepository`.
- **Denormalized totals**: `Trip.totalCost`/`Trip.nights` are recomputed client-side by
  each repository after every stop/expense mutation (`recomputeTotals`), reading Firestore
  from `Source.CACHE` (which includes pending writes, so it works offline). Any new
  mutation path must call it, or the trip list shows stale totals.
- **Cost semantics** (`domain/CostCalculator.kt`): camping cost lives **on the Stop**
  (`campingCostTotal`); the CAMPING expense type is only for extra site fees. Breakdown
  merges both into the CAMPING category. The fuel estimator (`domain/FuelEstimator.kt`)
  prefills distance as haversine leg-sum × `roadDistanceFactor` from settings; estimator-
  created expenses carry `isEstimate = true`. Trips with **no** recorded FUEL expense get
  an automatic fuel estimate (`FuelEstimator.autoTripFuelCost`, same distance formula)
  that is computed at display time, never stored: TripDetail and TripList add it to the
  shown total with a `≈` prefix (the denormalized `Trip.totalCost` stays actuals-only).
- **Navigation** (`ui/nav/`): type-safe kotlinx-serialization routes. The location picker
  returns its result through the **previous** back-stack entry's `SavedStateHandle` under
  `PICKED_LOCATION_KEY` (a `DoubleArray`); `AppNavHost` observes it and feeds
  `StopEditViewModel.setLocation`. StopEdit's GPS/map-picker buttons are injected by the
  nav layer as the `locationSection` slot composable.
- **Maps** (`ui/map/TripMap.kt`): one shared composable renders per-trip
  CircleLayer markers + LineLayer routes from GeoJSON built in code; stop/trip ids ride
  along as feature properties for click handling. Trip colors come from
  `tripColor(trip.id.hashCode())` so they're stable across screens. `AllTripsMapScreen`
  doubles as the single-trip fullscreen map via its nullable `tripId` filter.

## Verification expectations

Domain changes: `./gradlew test`. UI/map/location changes: verify in the emulator
(screenshots via `adb exec-out screencap -p`); map tiles need network, GPS needs a
`geo fix` plus the in-app permission grant. Firebase-mode changes can only be fully
tested after the FIREBASE_SETUP.md console steps; sign-in additionally needs a Google
account on the device. Commit per completed feature/milestone.
