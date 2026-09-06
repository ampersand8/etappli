# Conventions

## Code and docs

- **Short and concise.** Minimal prose, no boilerplate, no restating what the code
  says. Comments explain *why*, and the code base leans on them heavily: most design
  decisions are written where they apply. Read the KDoc before changing a rule.
- Prefer editing an existing doc over adding one. CLAUDE.md is the condensed reference
  and must stay in step with these docs when a rule changes.
- No lint or formatter; follow the surrounding style (official Kotlin style, trailing
  commas, expression bodies where they read well).
- Money through `formatCurrency(amount, settings.currency)`, dates through `ui/Format.kt`,
  decimal input through `DecimalField`/`parseDecimal` (comma or dot), dates through
  `DateField`.
- Colour language everywhere: blue = planned, green = active/current, grey = done
  (`ui/theme/StatusColors.kt`). In the timeline a stop's icon shape is its kind and its
  tint is its status.
- Commit per completed feature or milestone; commit messages read like a changelog
  line ("Check in by standing there: ten minutes at the planned stop").

## Hard rules

Each of these has a bug behind it. The KDoc at the call site says which.

1. **A stop change and the shift it causes are one `upsertStops` write**, never
   `upsertStop` in a loop. Both repositories settle the plan after every stop write.
2. **Every stop or expense mutation path recomputes totals** (`recomputeTotals` in both
   repositories), or the trip list shows stale numbers.
3. **Never `await()` a Firestore write.** Offline, the task completes only on server
   ack. Reads that must work offline use `Source.CACHE`.
4. **No collection-group queries.** The rules are path-scoped and reject them.
5. **Never write a `Stop` you did not just read.** A whole-stop write carries the
   track and legs it loaded; every service re-reads before writing.
6. **`Trip.totalCost` holds recorded numbers only.** Estimates are computed at display
   time and shown with ≈.
7. **Camping cost lives on the stop.** The CAMPING expense type is for extra fees only.
8. **Key nights and prices off `StopKind.isStay`**, never off naming VISIT or HOME.
9. **Never store a Places or Routes coordinate without its clock**, and never give a
   coordinate from GPS, a pin or a link one.
10. **Never trust a place id that did not come from `query_place_id`** in a share.
11. **Never show per-leg ascent next to a place name**; only `Stop.elevation` is shown.
12. **No background location, no sticky service, no boot receiver.** Tracking starts
    while the app is open, or not at all.
13. **Never `setIntent` in `MainActivity.onCreate`** (hangs the whole test suite).
14. **No endless `LaunchedEffect` tickers** (the Compose test clock never idles).
15. **Repository parity.** A behaviour change in one `TripRepository` implementation
    needs the mirror change in the other.
16. **Stay on the Essentials Routes SKU**: ≤ 10 intermediates, `TRAFFIC_UNAWARE`, no
    optimisation, per-leg field mask.
17. **Search and map are the same provider.** Places results may not be shown on a
    non-Google map.
18. **New JVM-pure logic goes into the PIT target lists; Robolectric-dependent classes
    never do.** New device-only code goes into `coverageExcludes`, sparingly.

## Recipes

Multi-file change recipes live in `.claude/skills/` and are written for a human as much
as for an assistant:

| Change | Skill | Summary |
| --- | --- | --- |
| Add or change a persisted field | `add-model-field` | Model default → `toMap()` and snapshot reader in both Firestore repositories → in-memory parity and seed → totals impact → UI input → tests |
| Add a screen or flow | `new-screen` | `@Serializable` route → ViewModel with `Factory` → screen with callbacks first, ViewModel last → `composable<Route>` in the NavHost → ViewModel + screen tests, PIT lists |
| Review a change | `app-review` | The invariants above as a checklist over the diff |
| Verify on the emulator | `verify-app`, `screens` | Boot, install, walk the screens, mock GPS, crash scan, screenshots |
| Build a signed APK | `release` | Keystore, signing config, the release SHA-1 for Firebase |

Yearly, with the `appVersionBase` bump: refresh `VignetteTable` prices, year and rates.

## Where to look

| Question | Start at |
| --- | --- |
| Why does the plan re-date itself? | `domain/DateCascade.settle`, `Timeline` |
| Why is this number ≈? | `domain/TripEstimator`, `FuelEstimator` |
| Why did a pin disappear? | `domain/PlaceCache`, `PlaceCacheSweeper` |
| Why is a leg a straight line? | `domain/RouteCache.usable`, `RouteRefresher`, `MapOverlay.segment` |
| Why did it check me in / not check me in? | `domain/Stay`, `RouteTracker.stay` |
| Why is nothing being recorded? | `Heading.underway`, `TrackingService.start`, `TrackingTrigger` |
| What is this tour called? | `domain/TripName`, `Trip.title` |
| Where does a shared link go? | `domain/SharedPlace.parse`, `ShareIntake`, `AddToTripViewModel` |
| What does the map draw? | `domain/MapOverlay`, `ui/map/GoogleMapProvider.Canvas` |
| What is stored where? | `data/FirestoreTripRepository` mapping section, [Domain model](02-domain-model.md) |
| What does the user see as text? | `ui/Format.kt` |
