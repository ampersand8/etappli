---
description: Add or change a persisted field on Trip/Stop/Expense/UserSettings — model, manual Firestore mapping in both repositories, UI input, seed data, tests. Use whenever a feature needs new stored data.
---

# Add a model field

There is no schema migration machinery: old Firestore docs simply lack the field, so
every read must tolerate its absence.

## Checklist

1. **`data/model/Models.kt`** — add the field with a sensible default. That default is
   also the value old documents deserialize to; choose it so existing data stays
   correct (nullable if "unset" is meaningful).
2. **Firestore mapping is manual — two places or the field silently doesn't persist:**
   - Trip/Stop/Expense: `toMap()` **and** the `DocumentSnapshot.toX()` reader in
     `FirestoreTripRepository`, null-safe (`getX(...) ?: default`). Types: `LocalDate`
     → `toEpochDay()` Long; `LocalTime` → `toSecondOfDay()` Long; `LatLng` → `GeoPoint`;
     enums → `name` string with `runCatching { valueOf }` fallback on read.
   - UserSettings: `update()` map **and** the snapshot reader in
     `FirestoreSettingsRepository` (defaults come from `UserSettings()`).
3. **`InMemoryTripRepository`** usually needs no mapping change, but keep behavior in
   parity with Firestore (ordering, defaults). Extend `seedDemoData()` if the field
   should show up in local demo mode.
4. **Totals impact**: if the field affects cost or nights, update
   `domain/CostCalculator` — both repos' `recomputeTotals` already funnel through it.
   Remember `Trip.totalCost` stays actuals-only; display-time estimates belong in
   `FuelEstimator` + the screens' `≈` handling.
5. **UI**: edit screens live in `ui/tripedit/` / `ui/tripdetail/ExpenseEditSheet` /
   `ui/settings/`. Numeric input = `DecimalField` + `parseDecimal` (comma or dot);
   dates = `DateField`; money display = `formatCurrency(amount, settings.currency)`.
   In ViewModels, keep free-text/decimal fields as `String` in UiState, parse on save.
6. **Tests**: extend the repo tests (`InMemoryTripRepositoryTest` /
   `InMemorySettingsRepositoryTest`), the touched ViewModel/screen tests, and
   `CostCalculatorTest`/`FuelEstimatorTest` if step 4 applied.
   `./gradlew test :app:coverageVerify :app:pitest` must all pass (Firestore repos are
   coverage-excluded — their mapping is exercised on-device only, so double-check
   step 2 by hand or in the emulator).
