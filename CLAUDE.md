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
./gradlew test                  # all unit + Robolectric/Compose UI tests
./gradlew :app:coverageVerify   # JaCoCo gate: 100% line coverage (device-only code excluded)
./gradlew :app:pitest           # mutation tests over JVM-pure logic, threshold 80%
./gradlew :app:installDebug     # install on connected device/emulator
./gradlew test --tests "com.nuelto.camperexperience.domain.CostCalculatorTest"  # one test class
```

CI (`.github/workflows/ci.yml`) runs tests + both gates + assembleDebug on every PR.
On pushes to main it also uploads `app/build/screenshots/` (main screens light+dark,
written by `ScreenshotsTest` during the normal test run) as a versioned artifact.

**Versioning**: `versionName` = `appVersionBase` (gradle.properties, major.minor) +
git commit count as patch; shown at the bottom of Settings. Bump `appVersionBase`
for milestones; the patch advances by itself. CI checks out full history so the
count is right.

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
- **Map provider is a seam** (`ui/map/MapProvider.kt`): what to draw is decided by pure
  `domain/MapOverlay.kt` (markers, route legs, camera frame — mutation-tested), and a
  provider renders it. `MapLibreProvider` (OpenFreeMap, no key) is the default;
  `GoogleMapProvider` takes over when `mapsApiKey` is in local.properties
  (`MapsBackend.kt`, mirroring FirebaseBackend). **The two switches are independent** —
  Firebase decides the store, the key decides the map. Tests provide
  `LocalMapProvider provides PlaceholderMapProvider`, which records camera moves so the
  picker's confirm flow still works on the JVM. Setup: GOOGLE_MAPS_SETUP.md.
- **Google mode searches via Places (New) Autocomplete** — Text Search resolves "grimsel"
  to one place, autocomplete offers the pass, the lake, the hospice and the hotel.
  Predictions carry no coordinate, so `PlaceSearch.resolve` fetches it via Place Details
  when a hit is chosen, inside the same billed session token. Tapping a POI Google already
  draws (`onPOIClick`) picks it directly — no lookup needed, it arrives with both.
  (`domain/GooglePlaces.kt` pure, `location/GooglePlacesSearch.kt` the HTTP calls);
  MapLibre mode stays on Photon —
  Places SST §14.2 forbids showing Places results on a non-Google map. §14.3 caps caching
  a Places coordinate at 30 days, so such stops carry `placeId` + `locationCachedAt`, and
  `domain/PlaceCacheSweeper` (run from TripDetailViewModel) refreshes them via Place
  Details or **deletes** them. Coordinates from GPS/crosshair/OSM have no
  `locationCachedAt` and never expire. Details: GOOGLE_MAPS_SETUP.md.
- A stop that came from Google keeps its place id, so `domain/MapsUri` can link back to
  the real place — "Open in Maps" and "Share" in the stop editor's location section.
- The picker is **search-and-choose, not aim**: hits come back as a tappable list (and
  as markers), press-and-hold drops a pin, and there is no crosshair. Nothing shows the
  user a coordinate — `StopEditUiState.locationLabel` says "Pin on map" rather than
  lat/lng when there is no name yet.
- Place search lives **on the picker map** (`ui/map/LocationPickerScreen.kt`), not in the
  stop form: hits land as markers, tapping one names it, and the picker returns that
  place's name alongside the coordinate (`PICKED_PLACE_KEY`) so the editor skips the
  reverse geocode. Backend is Photon (`photon.komoot.io`) — free, no API key, the app's
  only outbound HTTP call. `domain/Photon.kt` is pure (URL + lenient GeoJSON parse,
  mutation-tested); `location/PhotonPlaceSearch.kt` is a bare `HttpURLConnection` wrapper
  behind the `domain/PlaceSearch` interface. **`lang` only accepts default/de/en/fr** —
  anything else is a 400, so `Photon.language` clamps it. Fair-use only: debounced 300 ms,
  min 3 chars, biased to the map centre. `ui/map/**` is coverage-excluded, so
  `LocationPickerViewModel` earns its keep through pitest instead — it is in the target
  lists. Any failure just says so; the crosshair keeps working offline.
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
  manual (no Firestore reflection) in `FirestoreTripRepository`. Security rules are
  path-scoped, so collection-group queries are rejected — cross-trip reads
  (`allStops()`/`allExpenses()`) combine per-trip listeners instead.
- **Trip lifecycle**: `Trip.status` PLANNED → ACTIVE → DONE; one Timeline screen
  (TripDetail) serves all three with status-gated affordances. Stops carry `kind`
  (CAMPSITE/STELLPLATZ/FREE_CAMP/VISIT — visits are zero-cost route points) and `state`
  (PLANNED/DONE/SKIPPED — skipped stops stay in the record but count toward nothing).
  Legacy Firestore docs derive status from endDate (`legacyTripStatus`) — never PLANNED.
  Start-tour/plan-again copies go through `domain/TripStarter` (composed over the
  TripRepository interface — no dual-repo logic). The timeline is a list of
  `domain/Timeline` rows: stops plus a **GapRow per run of unplanned nights**, derived
  from the dates and never stored. Any row can be long-press dragged
  (`ui/components/Reorderable.kt` gesture + `ReorderState` math; `Timeline.move` rules —
  DONE stops are anchors nothing may cross, gaps stay between two stops), and gaps are
  resizable/deletable. Every such edit writes `reorderStops` + `DateCascade.resequence`,
  which re-dates the plan from the row order (a pure reorder keeps the trip's start and
  length); nights/arrival changes instead shift downstream dates (`DateCascade.shift`).
  Color language everywhere (lists, timeline, maps):
  **blue = planned, green = active/current, grey = done** (`ui/theme/StatusColors.kt`).
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
  that is computed at display time, never stored. **`Trip.totalCost` holds recorded
  numbers only** — display-time estimates never get denormalized; PLANNED/ACTIVE trips
  always render totals via `domain/TripEstimator` with a `≈` prefix. TripEstimator
  composes fuel (exactly one source: auto estimate only while zero FUEL expenses exist),
  camping (per stop: `costKnown ? campingCostTotal : nights × kind rate` from settings),
  road tax and other expenses. Vignette suggestions come from `domain/CountryGuess`
  (offline bounding boxes, confirm-only) + `domain/VignetteTable` — **refresh the table's
  prices yearly with the `appVersionBase` bump**.
- **Navigation** (`ui/nav/`): type-safe kotlinx-serialization routes. The location picker
  returns its result through the **previous** back-stack entry's `SavedStateHandle` under
  `PICKED_LOCATION_KEY` (a `DoubleArray`); `AppNavHost` observes it and feeds
  `StopEditViewModel.setLocation`. StopEdit's GPS/map-picker buttons are injected by the
  nav layer as the `locationSection` slot composable.
- **Maps** (`ui/map/TripMap.kt`): one shared composable renders per-trip
  CircleLayer markers + LineLayer routes from GeoJSON built in code; stop/trip ids ride
  along as feature properties for click handling. Colors follow the lifecycle: planned
  routes dashed blue, done grey, active trips segmented grey (done) / green (current
  leg) / dashed blue (ahead); visit stops render hollow, skipped stops leave the route.
  `AllTripsMapScreen` doubles as the single-trip fullscreen map via its nullable
  `tripId` filter.

## Testing conventions

UI tests run on the JVM: Robolectric + Compose test APIs (`robolectric.properties`
pins sdk/graphics/screen). `TestCamperApp` forces the in-memory container regardless
of a local google-services.json; `LocalMapEnabled provides false` swaps MapLibre for
a placeholder (`ui/map/TripMap.kt`). Fakes live in `testutil/`.

- **Screen tests**: `createComposeRule` + `@RunWith(AndroidJUnit4::class)` +
  `@Config(application = TestCamperApp::class)`; construct the ViewModel directly with
  `InMemoryTripRepository(seed = false)` etc. and pass it into the screen composable.
- **ViewModel tests**: plain JUnit with `MainDispatcherRule` (testutil) and Turbine
  for flow assertions. Async races (late geocode result, slow sign-in) use the gated
  fakes: `FakeAuthRepository.gate`, `FakePlaceNameResolver.gates`.
- **Coverage gate** is 100% of non-excluded lines — new code needs tests or, if
  genuinely untestable on the JVM (device/backend-only), an entry in
  `coverageExcludes` in app/build.gradle.kts.
- **pitest targets are explicit lists**: a new JVM-pure ViewModel or domain/data class
  worth mutating must be added to `--targetClasses`/`--targetTests` in
  app/build.gradle.kts; never add Robolectric-dependent classes (minions crash).
  Keep the 80% threshold green.

Multi-file change recipes live in `.claude/skills/`: **new-screen** (route + screen +
ViewModel + tests), **add-model-field** (model + Firestore mapping + UI + tests),
**app-review** (pre-commit invariant checklist).

## Verification expectations

Logic/UI changes: `./gradlew test :app:coverageVerify` (+ `:app:pitest` for domain/
data changes). Map/location changes: verify in the emulator
(screenshots via `adb exec-out screencap -p`); map tiles need network, GPS needs a
`geo fix` plus the in-app permission grant. Firebase-mode changes can only be fully
tested after the FIREBASE_SETUP.md console steps; sign-in additionally needs a Google
account on the device. Commit per completed feature/milestone.
