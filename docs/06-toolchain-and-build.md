# Toolchain and build

## Stack

| Piece | Version (see `gradle/libs.versions.toml`) | Notes |
| --- | --- | --- |
| Kotlin | 2.4.10, standalone Kotlin Gradle plugin | See the constraint below |
| Android Gradle Plugin | 9.2.1 | `compileSdk` 37, `targetSdk` 36, `minSdk` 28 |
| JVM target | 17 | CI runs Gradle on Temurin 21 |
| Jetpack Compose | BOM 2026.08.00, Material 3, extended icons | Compose compiler via the Kotlin plugin |
| Navigation Compose | 2.9.8 | Type-safe `@Serializable` routes (kotlinx-serialization) |
| Lifecycle / ViewModel | 2.11.0 | `collectAsStateWithLifecycle`, `viewModelFactory` |
| Google Maps | maps-compose 8.5.0, play-services-maps 20.0.0 | Map surface only; Places and Routes are plain HTTP |
| Location | play-services-location 21.4.0 | Fused provider, one-shot fix and the tracking service |
| Firebase | BOM 34.18.0: Auth, Firestore | Applied only when `google-services.json` exists |
| Sign-in | androidx.credentials 1.6.0 + googleid 1.2.0 | Credential Manager flow into Firebase Auth |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine 1.1.0, Robolectric 4.15.1, Compose UI test | All on the JVM |
| Gates | JaCoCo 0.8.13, PIT 1.19.1 (command line) | See [Testing](07-testing.md) |

One Gradle module, `:app`. The root project is still called `CamperExperience`
(`settings.gradle.kts`), the app's original name; the application id, namespace and
label are `com.nuelto.etappli` / Etappli.

## Toolchain constraint: standalone Kotlin

`gradle.properties` sets `android.builtInKotlin=false` and `android.newDsl=false`
because maps-compose 8.5+ needs Kotlin ≥ 2.4 metadata while every AGP release so far
bundles built-in Kotlin 2.2.x. Both opt-outs are removed in AGP 10 (expected late
2026): when an AGP with built-in Kotlin ≥ 2.4 exists, delete the two properties and the
`org.jetbrains.kotlin.android` plugin.

## Configuration inputs

| Input | Where | Effect when present | Effect when absent |
| --- | --- | --- | --- |
| `app/google-services.json` | gitignored, from the Firebase console | Applies the google-services plugin; the app runs in Firebase mode | Local demo mode: in-memory repositories seeded with four demo trips, no sign-in |
| `mapsApiKey` | `local.properties` (gitignored) or a Gradle property | `BuildConfig.MAPS_API_KEY` and the manifest meta-data; Google map, Places, Routes | No map, no search, no routing; straight lines and the road factor |
| `webClientId` | `local.properties`, falling back to `gradle.properties` (committed, not a secret) | Google Sign-In works | Sign-in shows a "missing webClientId" message |
| `camperUpload*` | `~/.gradle/gradle.properties` (outside the repo) or gitignored `keystore.properties` | Signed release bundle | Release builds still succeed, unsigned |
| `appVersionBase` | `gradle.properties` | major.minor of the version | defaults to 0.1 |

Runtime modes are the product of the first two switches; every combination runs.

| | Maps key | No key |
| --- | --- | --- |
| **Firebase** | Full app | Synced trips, blank map |
| **No Firebase** | Demo data, Google map | Demo data, blank map (what CI and tests run) |

## Versioning

`versionName` = `appVersionBase` + git commit count as the patch; `versionCode` = the
commit count. Shown at the bottom of Settings. Bump `appVersionBase` for milestones
(and refresh `VignetteTable` at the same time); the patch advances by itself. CI checks
out full history so the count is right; a shallow clone counts fewer commits and Play
refuses a `versionCode` it has already seen.

## Commands

```bash
./gradlew :app:assembleDebug    # build
./gradlew test                  # all unit + Robolectric/Compose UI tests
./gradlew :app:coverageVerify   # JaCoCo gate: 100% line coverage (device-only code excluded)
./gradlew :app:pitest           # mutation tests over JVM-pure logic, threshold 80%
./gradlew :app:installDebug     # install on a connected device/emulator
./gradlew :app:bundleRelease    # Play bundle; unsigned unless the upload key is configured
./gradlew :app:testDebugUnitTest --tests "com.nuelto.etappli.domain.CostCalculatorTest"
```

There is no lint or formatter configured; `kotlin.code.style=official` is the only
style setting.

## CI

`.github/workflows/ci.yml`, on every pull request and push to `main`:

1. Checkout with full history (for the version).
2. `:app:testDebugUnitTest :app:coverageVerify`.
3. `:app:pitest`.
4. `:app:assembleDebug`.
5. On pushes to `main`: upload `app/build/screenshots/` (main screens, light and dark,
   written by `ScreenshotsTest` during the normal test run) as a versioned artifact.
6. Always: upload test, coverage and PIT reports (14 days).

CI never has a Maps key or Firebase config; everything it runs is the no-Firebase,
no-key mode.

## Release and Play

Release signing, the Play Console steps, the data-safety answers and the three things
that break after the first upload (Firebase needs the Play app-signing SHA-1; the Maps
key ships unrestricted; the foreground-service declaration) are in
[PLAY_STORE_SETUP.md](../PLAY_STORE_SETUP.md). Listing copy and graphics are in
`play/listing/`, the policy in [PRIVACY.md](../PRIVACY.md). `isMinifyEnabled` is off.

The Firebase project is `etappli` (`.firebaserc`), Firestore in `eur6` (Zurich); the
security rules are `firestore.rules` and the one index exemption is
`firestore.indexes.json`, both pasted into the console by hand rather than deployed by
the CLI.

## Manifest

- Permissions: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. No
  background location.
- `MainActivity`: launcher, `singleTop`, `adjustResize`; intent filters for
  `ACTION_SEND text/plain` (labelled "Add to a trip" in the share sheet) and `geo:`.
- `location.TrackingService`: not exported, `foregroundServiceType="location"`.
- `com.google.android.geo.API_KEY` meta-data from the `mapsApiKey` placeholder.

Edge-to-edge is enabled with `targetSdk` 36, so the app draws under the system bars.
Scaffold padding covers screens; a `bottomBar` slot and `ModalBottomSheet` content need
their own insets padding, and Robolectric renders no system bars, so only a device
shows a mistake there.

## Emulator

An AVD named `Pixel_9a` with a Play services image is the local target (sign-in, fused
location and reverse geocoding need Play services). GPS is mocked with
`adb emu geo fix <lon> <lat>` (longitude first). The `verify-app` and `screens` skills
in `.claude/skills/` script the walk-through and the screenshot audit; CLAUDE.md has the
raw commands.
