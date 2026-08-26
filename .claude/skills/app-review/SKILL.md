---
description: Review changed code against this repo's specific invariants (totals denormalization, offline-safe Firestore usage, repo parity, cost semantics). Use before committing a feature or when asked to review changes.
---

# App-specific review checklist

Review the current diff (`git diff` / `git diff HEAD`) against these invariants. These are the bugs generic review misses because they're project conventions:

## Data invariants

- **Every stop/expense mutation path recomputes trip totals.** Any new call site that writes stops or expenses must trigger `recomputeTotals(tripId)` (both `FirestoreTripRepository` and `InMemoryTripRepository`), or `Trip.totalCost`/`nights` go stale in the trip list.
- **Firestore writes are fire-and-forget.** Never `await()` a `set`/`update`/`delete` — with offline persistence the Task only completes on server ack, so awaiting hangs the UI offline. Reads that must work offline use `Source.CACHE` (which includes pending writes).
- **Repository parity.** `InMemoryTripRepository` and `FirestoreTripRepository` implement the same interface and must stay behaviorally equivalent (ordering, id generation on blank id, cascade delete of stops/expenses with the trip). A change to one usually needs the mirror change.
- **Camping cost lives on the Stop** (`campingCostTotal`). The CAMPING expense type is only for extra fees not tied to a stay; `CostCalculator.breakdown` merges both. Don't introduce double-counting.
- Dates are `LocalDate` in models, epoch-day `Long` in Firestore. Mapping is manual in the Firestore repos — new fields need both `toMap()` and the snapshot reader, with a null-safe default.

## UI conventions

- Money is formatted with `formatCurrency(amount, settings.currency)` — currency always comes from settings (CHF default), never hardcoded.
- Decimal input accepts comma or dot: use `parseDecimal`/`DecimalField` from `ui/components/Fields.kt`, not `toDouble()`.
- New ViewModels follow the companion `Factory = containerViewModelFactory { … }` pattern; dependencies come from `AppContainer` only.
- New screens/routes: `@Serializable` route in `ui/nav/Routes.kt` + `composable<Route>` in `AppNavHost`. Results back to a previous screen go through the previous back-stack entry's `SavedStateHandle` (see `PICKED_LOCATION_KEY`).
- Map work stays inside `ui/map/TripMap.kt`; trip colors via `tripColor(trip.id.hashCode())` for cross-screen stability.

## Auth-mode blind spots

- Code must not assume Firebase: `container.authRepository` is null in local mode. Anything touching auth needs the null path.
- In Firebase mode, ViewModels may assume a signed-in user (the auth gate guarantees it) — but only ViewModels, not Application-scoped code.

## Finish

Run `./gradlew test` for domain changes. For UI changes, run the **verify-app** skill. Report findings ranked by severity; apply fixes only if the user asked for a review-and-fix.
