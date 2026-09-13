# Business

## What Etappli is

A personal Android app for camper travel. It answers two questions: **what will this
tour cost before I leave**, and **what did it actually cost afterwards**, with the route
drawn on a map along real roads. One user (the author, Simon), one vehicle, trips mostly
in Switzerland and neighbouring countries, money in CHF by default.

It replaces the spreadsheet a camper traveller keeps: stops with nights and site fees,
fuel, motorway vignettes, and the odd cable-car ticket. The app is deliberately small.
There are no accounts to share, no social features, no analytics.

## Core concepts

- **Trip.** A journey with a status: PLANNED (a *tour* or *plan*), ACTIVE (on the road),
  DONE (a record). One screen, the timeline, serves all three with status-gated actions.
- **Stop.** A place on the trip, in order, with an arrival date and a number of nights.
  Its **kind** says what it is: campsite, Stellplatz, free camp, visit, or home. Only the
  first three are *stays* (nights and a price make sense). Its **state** says whether it
  is still planned, reached (DONE), or skipped.
- **Expense.** Money spent on the trip that is not a site fee: fuel, road tax, other, or
  an extra camping fee. Site fees live on the stop itself.
- **Home.** A pin in Settings. A new plan starts and ends there as two real HOME stops,
  so the drive out and the drive back are counted without special cases.
- **Settings.** Currency, fuel consumption and price, vehicle weight, a road-distance
  fallback factor, and default per-night rates used to estimate unpriced stays.

## User journeys

**Plan a tour.** Tap "Plan a tour". No form: a plan is created on the spot (dated today,
home at both ends if a home is set) and the stop editor opens over its timeline. Add
stops by searching a place, tapping a point of interest on the map, pressing and holding
to drop a pin, or using the current GPS position. Each stop takes a date and nights;
everything behind it moves along. Drag rows to reorder, leave nights unplanned, insert a
stop or a free night between any two rows. The plan is named after the regions it visits
unless you type a name. The total shows as ≈ because unpriced stays and fuel are
estimated.

**Start the tour.** "Start tour" picks the start day, optionally keeps the plan as a
template (a copy becomes ACTIVE), snapshots the estimate for a planned-versus-actual
comparison, and checks in the home you set out from.

**On the road.** The NowCard shows the stop you are heading for, how far it still is
from where you are, and when you arrive. Opening the app on a travelling day starts a
foreground service that records the road driven every two minutes and, after ten minutes
standing at the stop, checks you in automatically. Check-in stamps the arrival time and
shifts the rest of the plan by any lateness. Tapping ✓ Arrived asks for the real site
price, and a background check-in leaves the ≈ price on the card to tap; either turns the
estimate into a recorded cost. Skip a stop you did not reach, check out early, undo a
wrong check-in, add fuel and other expenses as they happen.

**Afterwards.** "Finish trip" marks it DONE. Totals are recorded numbers only, broken
down into camping, fuel, road tax and other. "Plan again" copies a finished or active
trip into a fresh plan.

**Log a past trip.** "Log a trip" keeps a small form (name optional, dates, notes) for
trips that happened before the app existed; stops and expenses are then added as usual.

**Share a place in.** Google Maps → Share → Etappli (or any `geo:` link). The app parses
the shared text, follows a short link if needed, looks a name-only share up, asks which
trip it belongs to (or plans a new tour for it), and opens the stop editor with the place.

**See everything on one map.** All trips on a single map: planned blue, active green,
done grey; tap a marker for the trip and stop.

## Money: recorded versus estimated

The app never mixes the two silently.

- **Recorded** numbers are what you paid: a stop's camping cost once its price is known,
  and every expense. They sum into the trip total stored on the trip.
- **Estimated** numbers fill the blanks while planning or travelling: nights × the
  kind's default rate for an unpriced stay, and a fuel estimate from the routed distance
  and the climb when no fuel has been logged yet. They are computed when displayed,
  never stored, and always shown with ≈.

Full rules in [Costing](04-costing.md).

## Product principles and why

- **No forms where a stop will do.** A tour has no name or dates of its own; its stops
  carry them. Names are derived from where the stops are, dates from the first stop.
- **The plan cannot contradict itself.** Nobody sleeps in two places on one night, so
  every stop write settles the dates behind it. A late arrival shifts what follows.
- **Roads, not straight lines.** Distances, times and fuel come from routed legs; a
  straight line scaled by a factor is only the fallback when nothing routed the drive.
- **Honest numbers.** Estimates are marked, elevation-based climb is an order of
  magnitude, and constants nobody has validated say so in the code.
- **Offline first.** Firestore's local cache queues every write; nothing awaits a
  server acknowledgement, so the app works with no signal and syncs later.
- **Private by construction.** No analytics, no ads, no third-party trackers. Location
  is used on tap and while a drive of an active trip is tracked, under a visible
  notification, and never in the background without the app having been opened.
- **Google's terms are enforced, not ignored.** Places and Routes coordinates may be
  cached for 30 days; the app refreshes or deletes them on schedule.
- **Degrades gracefully.** No Maps key: no map, everything else works. No Firebase
  config: local demo mode with seeded trips. No signal: straight lines and factors.

## Known limits

- One user per install; no sharing of trips between accounts.
- A drive on a day the app is never opened is not recorded (no background location).
- Elevation comes from a terrain model, so tunnels look like mountains; the climb term
  is clamped and smoothed but stays approximate, and its fuel constants are unvalidated.
- Vignette prices and exchange rates are bundled and refreshed yearly by hand.
- Country detection for vignettes is by bounding box: over-suggests near borders and
  misses transit-only countries.
- Currency conversion exists only for vignette prices; everything else is entered in
  the app currency.
- The Maps API key ships in the APK; restrict it to the app's certificates and APIs
  (GOOGLE_MAPS_SETUP.md).
- Google's logo attribution requirement is met with text only (see GOOGLE_MAPS_SETUP.md).
