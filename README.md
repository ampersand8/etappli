# Etappli

Personal Android app for tracking camper trips: where you went, how many nights you
stayed at each stop, and what the trip cost (camping + fuel + road taxes) — with all
trips visible on a map.

## Features

- **Trips with multiple stops** — each stop has its own location, arrival date,
  nights, and camping cost; fuel/road-tax/other expenses live on the trip.
- **Cost breakdown** per trip (camping / fuel / road tax / other) and total,
  shown in the trip list and on the trip detail screen. Default currency CHF,
  changeable in Settings.
- **Fuel estimator** — prefills trip distance from the stops (straight line × road
  factor, both configurable) and computes distance × consumption × price; manual
  fill-ups are just regular expenses.
- **Maps** (Google Maps — needs an API key, see
  [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md)) — all-trips overview with per-trip
  colors and route lines, per-trip map on the detail screen, tap a marker for
  trip/stop info.
- **Locations** via one-shot GPS fix ("I'm here") or the map picker — search a place
  by name and choose from the results, tap a POI Google already shows, or press and
  hold to drop a pin. Results favour whatever kind of stop you're adding.
- **Share a place into the app** — Google Maps (or anything sending a `geo:` link) →
  Share → Etappli: pick the trip and the stop editor opens on that place.
- **Cloud sync** via Firebase (Google Sign-In + Firestore with offline persistence).
  Until Firebase is configured the app runs in local demo mode — see
  [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + repository, hand-rolled DI ·
Compose Navigation (type-safe routes) · Google Maps Compose + Places · Firebase Auth/Firestore.

## Build & run

```bash
./gradlew :app:installDebug   # build + install on connected device/emulator
./gradlew test                # domain unit tests (costs, fuel estimate, haversine)
```

Requires JDK 17+ and an Android SDK with platform 37 (`local.properties` →
`sdk.dir`). Use an emulator image with Play services (needed for sign-in and
fused location); mock GPS via the emulator's extended controls.

### Releasing

```bash
./gradlew :app:bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

Signing comes from `camperUpload*` properties in `~/.gradle/gradle.properties` (or a
gitignored `keystore.properties`); without them the bundle builds unsigned. Play Console steps, listing copy and graphics:
[PLAY_STORE_SETUP.md](PLAY_STORE_SETUP.md), `play/listing/`,
[PRIVACY.md](PRIVACY.md).

### Installing on a physical phone

One-time phone setup:

1. Enable Developer Options: Settings → About phone → tap **Build number** 7 times.
2. Settings → System → Developer options → enable **USB debugging**.
3. Connect the phone via USB. On the phone, accept the **Allow USB debugging?**
   dialog (tick "Always allow from this computer" to skip it next time).

Then, from the project root:

```bash
~/Android/Sdk/platform-tools/adb devices   # phone should show as "device", not "unauthorized"
./gradlew :app:installDebug                # build + install the debug APK
```

The app appears in the launcher as **Etappli**. Re-running
`installDebug` updates it in place (data is kept). Notes:

- This is the **debug** build. Without `app/google-services.json` it runs in
  local demo mode; with it, Google Sign-In works on a real phone out of the box
  (real phones have Play services).
- If the USB cable only charges, it may be a power-only cable — use a data cable.
- Alternative, cable-free: Developer options → **Wireless debugging** → pair via
  `adb pair <ip>:<port>` once, then `adb connect <ip>:<port>` on the same Wi-Fi.
