# Costing

Two kinds of number, never mixed silently: **recorded** (what was paid) and
**estimated** (what it will probably cost). Recorded numbers are stored; estimates are
computed at display time and prefixed with ≈.

## Recorded costs: `CostCalculator`

- `tripTotal` = camping cost of every non-skipped stop + every expense amount.
- `tripNights` = nights of every non-skipped stop.
- `breakdown` = CAMPING (stops' `campingCostTotal` plus CAMPING-typed expenses), FUEL,
  ROAD_TAX, OTHER; zero categories are omitted.

These feed the denormalized `Trip.totalCost` / `nights` and the breakdown card on a
DONE trip. Skipped stops were never paid for, so they count toward nothing.

The site fee lives **on the stop** (`campingCostTotal`), not as an expense. The CAMPING
expense type exists only for extra site fees (a second pitch, a late fee) so nothing is
counted twice.

## Estimates: `TripEstimator`

For PLANNED and ACTIVE trips the timeline, the trip list and the all-trips map show
`TripEstimator.estimate(stops, expenses, settings)`:

| Component | Rule |
| --- | --- |
| Fuel | **Exactly one source.** While the trip has *no* FUEL expense at all, the automatic estimate from distance and climb. Once any FUEL expense exists, the sum of FUEL expenses (marked estimated if any carries `isEstimate`). |
| Camping known | Stops with `costKnown` (their `campingCostTotal`) plus CAMPING expenses |
| Camping estimated | For stops with `costKnown = false`: nights × the kind's rate from Settings (campsite 45, Stellplatz 15, free camp 0) |
| Road tax, other | Sum of the respective expenses |
| `hasEstimates` | Whether anything above is an estimate, which decides the ≈ prefix |

Typing the real price at check-in (the arrival price sheet, or tapping the ≈ price on
the NowCard) sets `costKnown = true`, which is the whole plan-to-actual reconciliation
for a stay. Starting a tour snapshots `estimate.total` and the nights into
`Trip.plannedCost` / `plannedNights` for comparison afterwards.

DONE trips show recorded totals. The trip list additionally shows a fuel estimate for a
done trip that never had fuel logged, so an old record still gets a plausible number.

## Fuel: `FuelEstimator`

```
liters = distanceKm × consumption / 100 + climbLiters
cost   = liters × pricePerLiter
```

**Distance** (`defaultTripDistanceKm`) sums `RouteCache.drives(stops)`: consecutive
located, non-skipped stops that are not at the same spot. Each drive uses the routed
road distance when the stored leg still describes it and has a road, otherwise
haversine × `roadDistanceFactor` (default 1.25). The factor is the fallback, not the
rule: it covers drives that were never routed because there was no key, no signal, or
no route.

**Climb** (`climbLiters`) prices the ascent of each leg by the gravity term of the
road-load equation:

```
liftLiters(mass, ascent) = mass × 9.81 × ascent / (0.32 × 35.8 MJ/l)
                         ≈ 0.086 l per tonne per 100 m
```

Only the gravity term: rolling resistance and drag are already inside the user's
measured l/100 km, so adding them would count them twice. A descent gives back at most
85% of the smaller of "the lift it would have cost" and "the fuel that stretch would
have burned anyway" (a non-hybrid can only shut its injectors on the overrun). Legs
without an elevation figure contribute nothing, so the estimate degrades to distance
only rather than to nonsense.

**The constants are unvalidated against a real tankful.** The physics is right, but the
drivetrain efficiency is a single number standing in for an engine map. Treat the climb
term as an order of magnitude; the ascent figures feeding it are themselves only stable
to about 6% (see [elevation](05-location-and-routing.md#elevation)).

### The manual estimator sheet

The expense sheet's "Estimate from distance…" section prefills the distance with
`defaultTripDistanceKm`, lets the user edit distance, consumption and price, and saves a
FUEL expense with `isEstimate = true`. From then on the automatic estimate is off for
that trip (one source rule), so the user's number wins.

## Vignettes

Road tax for the countries a camper crosses is usually a prepaid vignette. The app
suggests one per country a stop falls into:

1. `CountryGuess` maps stop coordinates to countries with coarse offline bounding boxes
   (CH, AT, SI, CZ). Confirm-only: over-suggesting near a border is harmless, and a
   transit-only country with no stop is missed, which is why the expense sheet still
   has a manual road-tax entry.
2. `daysIn` = nights in the country + the arrival day.
3. `VignetteTable.cheapestCovering` picks the cheapest combination of the bundled
   vignettes (1-, 10-, 30-day, annual) covering those days, converts at bundled rough
   rates into the app currency, and the suggestion becomes a ROAD_TAX expense with
   `isEstimate = true` and a label starting with the country code and ending with the
   table year.
4. A country with any ROAD_TAX expense whose label starts with its code is not
   suggested again.

`VignetteTable.YEAR`, the prices and the exchange rates are facts refreshed by hand
once a year, together with the `appVersionBase` bump. No free toll API exists.

## Where each number is shown

| Screen | PLANNED / ACTIVE | DONE |
| --- | --- | --- |
| Trip list card | ≈ estimate total | recorded total, or ≈ fuel estimate if no fuel was logged |
| Trip detail | `EstimateCard` (fuel, camping known/estimated, road tax, other, ≈ total), NowCard ≈ price for an unpriced stay | `CostBreakdownCard` (recorded) |
| Timeline stop row | price, or "≈ nights × rate" while unpriced | recorded price |
| All-trips map card | ≈ estimate | recorded total |

Money is always formatted with `formatCurrency(amount, settings.currency)`; the
currency is never hardcoded. Decimal input accepts comma or dot (`parseDecimal`).
