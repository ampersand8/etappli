# Testing

Everything runs on the JVM, and two gates make the suite the specification: **100% line
coverage** of everything that can run there, and **≥ 80% mutation score** on the pure
logic. A change without tests fails CI.

## Test types

| Type | Setup | Examples |
| --- | --- | --- |
| Domain unit tests | Plain JUnit | `DateCascadeTest`, `RouteRefresherTest`, `SharedPlaceTest`, `StayTest` |
| Repository tests | Plain JUnit on the in-memory implementations | `InMemoryTripRepositoryTest` |
| ViewModel tests | `MainDispatcherRule` (testutil) replaces `Dispatchers.Main`; Turbine for flow assertions; route args through `SavedStateHandle(mapOf(...))` | `TripDetailViewModelTest`, `StopEditViewModelTest` |
| Screen tests | Robolectric + Compose: `createComposeRule`, `@RunWith(AndroidJUnit4::class)`, `@Config(application = TestCamperApp::class)`; the ViewModel is constructed directly with `InMemoryTripRepository(seed = false)` and passed in | `TripDetailScreenTest`, `LocationPickerScreenTest` |
| Wiring and flow tests | `createAndroidComposeRule` / `ActivityScenario` | `AppWiringTest`, `MainActivityTest`, `ShareIntentTest`, `AppNavHostTest` |
| Screenshots | `ScreenshotsTest` renders the main screens with seed data, light and dark, into `app/build/screenshots/`; not an assertion, an artifact | |

`robolectric.properties` pins SDK 35, native graphics and a phone-sized screen.
`TestCamperApp` forces the in-memory container regardless of a local
`google-services.json`, and every map-bearing test provides
`LocalMapProvider provides PlaceholderMapProvider`.

## Fakes and gates (`testutil/TestSupport.kt`)

Async races are tested with gated fakes rather than sleeps: a fake suspends on a
`CompletableDeferred` until the test releases it, so "a late geocode result must not
overwrite what the user typed meanwhile" is a deterministic assertion.

- `FakeAuthRepository.gate` — a slow sign-in.
- `FakePlaceNameResolver.gates` — a late reverse geocode, one gate per call.
- `FakePlaceSearch` — records queries, biases and preferred kinds; gated search and find.
- `FakeShareLinkResolver` — a short link that resolves on demand.
- `GatedSettingsRepository` — settings that arrive mid-tap.
- `PlaceholderMapProvider` — a recording camera; tapping the placeholder stands in for
  the long press, so the picker's drop-a-pin flow runs end to end.

## The coverage gate

`:app:coverageVerify` requires 100% line coverage after excluding code that needs a
real device or backend (`coverageExcludes` in `app/build.gradle.kts`): `BuildConfig`,
the Firebase and Maps backend switches, the Firestore repositories and auth, all of
`location/`, and all of `ui/map/` (the Google provider and the screens that host it).
The excluded code is verified on the emulator instead. New device-only code needs an
entry there; excluding something that could be JVM-tested is a review finding.

Because the Firestore repositories are excluded, **their manual field mapping is not
covered by any test**: a new model field must be checked in both `toMap()` and the
snapshot reader by hand or on a device.

## Mutation testing

`:app:pitest` runs PIT over explicit lists of classes and tests: `domain.*`,
`data.InMemory*`, the models, `ui/Format.kt`, `StatusColors`, `ReorderState`, and the
JVM-pure ViewModels (trip list, settings, location picker, add-to-trip). The lists are
explicit because Robolectric-dependent classes crash PIT's minion processes. A new pure
ViewModel or domain class worth mutating goes into both `--targetClasses` and
`--targetTests`. Companions, inlined coroutine machinery and Kotlin null-check
intrinsics are excluded as noise. Threshold: 80%.

## Conventions and pitfalls

- Never replace the activity's intent in `MainActivity.onCreate` (`setIntent`):
  `ActivityScenario` then never sees RESUMED and every `createAndroidComposeRule` test
  hangs with no failure, just a stuck suite.
- Never leave an endless `LaunchedEffect` ticker in a screen: the Compose test clock
  never goes idle. The arrival time on the NowCard is read at composition for exactly
  this reason.
- Screen tests record navigation callbacks into a list and assert on it; they never
  navigate for real.
- Robolectric renders no system bars, so inset mistakes (bottom bars, bottom sheets)
  are invisible to the suite.
- Seeded demo data (`InMemoryTripRepository(seed = true)`) is for screenshots and demo
  mode; tests construct the state they need with `seed = false`.

## What only a device can verify

Map tiles, marker rendering, Places and Routes calls with a real key, GPS through the
fused provider, the foreground service and its notification, Google Sign-In, Firestore
sync and offline queueing, and insets. The `verify-app` skill walks the core screens on
the emulator with screenshots and a crash scan; Firebase-mode changes additionally need
the FIREBASE_SETUP.md console steps and a Google account on the device.

## Running

```bash
./gradlew test :app:coverageVerify          # logic or UI change
./gradlew :app:pitest                       # plus this for domain/data changes
./gradlew :app:testDebugUnitTest --tests "com.nuelto.etappli.domain.StayTest"
```
