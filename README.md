# CamperExperience

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
- **Maps** (OpenStreetMap via MapLibre + OpenFreeMap, no API key; optionally Google
  Maps — see [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md)) — all-trips
  overview with per-trip colors and route lines, per-trip map on the detail screen,
  tap a marker for trip/stop info.
- **Locations** via one-shot GPS fix ("I'm here") or the map picker — where you can
  also search for a place by name (Photon/OpenStreetMap, no API key) and pick the hit
  that sits in the right spot. Offline, the crosshair still works.
- **Cloud sync** via Firebase (Google Sign-In + Firestore with offline persistence).
  Until Firebase is configured the app runs in local demo mode — see
  [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + repository, hand-rolled DI ·
Compose Navigation (type-safe routes) · MapLibre Compose · Firebase Auth/Firestore.

## Build & run

```bash
./gradlew :app:installDebug   # build + install on connected device/emulator
./gradlew test                # domain unit tests (costs, fuel estimate, haversine)
```

Requires JDK 17+ and an Android SDK with platform 37 (`local.properties` →
`sdk.dir`). Use an emulator image with Play services (needed for sign-in and
fused location); mock GPS via the emulator's extended controls.

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

The app appears in the launcher as **CamperExperience**. Re-running
`installDebug` updates it in place (data is kept). Notes:

- This is the **debug** build. Without `app/google-services.json` it runs in
  local demo mode; with it, Google Sign-In works on a real phone out of the box
  (real phones have Play services).
- If the USB cable only charges, it may be a power-only cable — use a data cable.
- Alternative, cable-free: Developer options → **Wireless debugging** → pair via
  `adb pair <ip>:<port>` once, then `adb connect <ip>:<port>` on the same Wi-Fi.
