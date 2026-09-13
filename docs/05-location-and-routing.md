# Location and routing

Everything that touches the outside world, and the rules that keep it honest. All of
it degrades: no key, no signal or a junk response leaves stored data alone and the app
falls back to straight lines, factors and names it already has.

## External services

| Service | Used for | Key / cost | Retention |
| --- | --- | --- | --- |
| Maps SDK for Android (maps-compose) | Drawing the map, markers, polylines, POI taps | `mapsApiKey`; no per-load charge | n/a |
| Places API (New), web service | Autocomplete while typing, Text Search on submit, Place Details for the chosen hit, photos | same key; billed per session / per request, field masks pick the tier | coordinate 30 days (SST §14.3), place id forever |
| Routes API `computeRoutes`, web service | Road legs between stops, transit routes for park and ride, live drive from here | same key; Essentials SKU by design | routed coordinates 30 days (SST §19.3) |
| Open-Meteo elevation (Copernicus DEM GLO-90) | Stop height and per-leg climb | no key; licence asks for attribution (Settings footer) | stored, open data |
| Platform `Geocoder` (Play services) | Naming a pin, and the region of each stop | none; silently null without Play services | stored |
| Fused location | The picker's my-location fix, and the tracking service's fixes | none; needs `ACCESS_FINE_LOCATION` | fixes stored as the track |

Places and Routes are called as plain web services through `location/Http` (no
Places SDK: 30-odd transitive dependencies for View widgets the app does not use, and
parsing hidden from tests). It sends `X-Android-Package`/`X-Android-Cert`, read from
the app's own signature at startup (`AppIdentity`, from MapsBackend), so the key can
carry an Android *application* restriction — and it logs every failed call under the
`Http` tag, so "Search unavailable" has a reason in `adb logcat -s Http`.
Request building and parsing are pure and mutation-tested in `domain/GooglePlaces`,
`domain/GoogleRoutes`, `domain/Elevation`; the transport in `location/` is fail-soft
and coverage-excluded.

## Place search (`domain/PlaceSearch`, `location/GooglePlacesSearch`)

The picker works like a maps app: **typing predicts, submitting drops pins.**

- **Typing** goes to Autocomplete, debounced (300 ms, min 3 chars), biased to the map
  centre within 50 km. Autocomplete rather than Text Search because "grimsel" should
  offer the pass, the lake, the hospice and the hotel, not resolve to one of them. A
  second pass filtered by `includedPrimaryTypes` for the kind of stop being added
  (campground, rv_park, parking/rest_stop, tourist_attraction) is merged ahead of the
  open results (`mergePreferred`), which surfaces campsites the open query misses.
- **Submitting** (IME action or the field's icon) goes to Text Search with the Pro-tier
  field mask: a name gives the one place, which opens by itself; a kind of place gives
  up to 20 located hits shown as hollow pins plus a strip of cards.
- **Choosing** a prediction, a pin, or a POI Google already draws calls
  `PlaceSearch.resolve`, which fetches Place Details for the coordinate and what the
  place is like (type, rating, editorial summary, first review, one photo). The photo
  comes back as bytes and is decoded in `ui/map`, so the picker's state stays free of
  Android graphics types. The details field mask moves that one call into a higher
  billing tier; that is the price of knowing whether to stay there.
- **Session tokens** group the keystrokes and the resolving Place Details call into one
  billable autocomplete session; the token is retired once Details has been fetched.
- **Back** peels the picker's layers innermost first: full card → strip → pins → search
  → close.
- **My location** pins the GPS fix, labelled "Your location". The permission is asked
  for on tap; a fix lands only while still awaited — typing, choosing or pressing the
  map meanwhile has moved on from it. This is the one GPS entry point for a stop: the
  editor and Settings offer just "Pick spot" (`LocationSection`). The button takes the
  corner of Google's zoom buttons, so the picker turns those off (`Canvas(zoomControls)`).

A chosen place names the stop. A dropped pin or a fix has no name, so the editor reverse-geocodes
one (`PlaceNameResolver.placeName`), which only ever fills a blank or previously
auto-filled name.

## The 30-day rules

Google allows caching a Places coordinate for 30 consecutive days (§14.3) and grants the
same window to routed coordinates (§19.3). Place ids may be kept indefinitely.

| Data | Clock | Owner | On expiry |
| --- | --- | --- | --- |
| `Stop.location` with `locationCachedAt` | `PlaceCache` | `PlaceCacheSweeper`, on opening a trip | Place Details refetches by `placeId`; if that fails the coordinate is **deleted** and the stop keeps its id until the next online sweep |
| `Stop.leg` (`fetchedAt`) | `RouteCache` | `RouteRefresher`, on opening a trip and on every pin change | The leg is dropped and refetched; if that fails the map draws a straight hop and the estimate uses the road factor |

Coordinates from GPS, a dropped pin, or lifted from a shared link have no
`locationCachedAt` and never expire. A share whose coordinate had to be looked up on
the Places API (`SharedPlace.fromPlaces`) does get the clock. A wrong place id would
have the sweeper delete a pin 30 days on, which is why the share parser only trusts
`query_place_id`, never an ftid or cid.

Cost of the design: a Places-sourced stop older than 30 days loses its pin until the app
is next opened online. Trips under a month are unaffected while they matter.

## Routing (`domain/GoogleRoutes`, `domain/RouteRefresher`)

- A drive is a pair of consecutive located, non-skipped stops at different spots
  (`RouteCache.drives`); a fresh plan that leaves home and comes straight back has none.
- `RouteRefresher.refresh` clears stale legs (mismatched endpoints or expired), fills
  missing stop elevations, and if any drive lacks a usable leg refetches the **whole
  chain**. One request covers a normal trip, and a partial fetch would have to reason
  about which stored leg belongs to which pair after a reorder, which is the bug class
  the endpoint comparison exists to make impossible.
- Requests stay on the Essentials SKU: ≤ 10 intermediates per call
  (`GoogleRoutes.windows` splits longer trips into overlapping windows of 12 points),
  `TRAFFIC_UNAWARE`, no waypoint optimisation, per-leg field mask only. Intermediates
  are plain waypoints, not `via`, so Google returns one leg per pair.
- A window with a malformed or misaligned answer discards the whole response rather
  than shifting legs onto the wrong stops.
- Each leg gets its climb from the elevation service (below) and is written back onto
  the arriving stop after a re-read, keeping an already-measured climb if the new
  elevation call failed.

### A stop no road reaches (`domain/ParkAndRide`)

A car-free village (Riederalp) empties the whole `computeRoutes` answer for its window.
The refresher then asks that window drive by drive; a drive that still has no road goes
park and ride: a TRANSIT route to the stop says where its last ride boards, the vehicle
is left there (`TransitRide.parked`), the road legs run to and from that spot, and the
ride sits on the leg either side (`rideAfter` up, the next leg's `rideBefore` down).
Up to three rides back are tried when no road reaches the boarding point either. The
ride is dotted on the map and shown as an icon with minutes in the timeline
(`TravelMode`; `ui/components/TravelIcons.kt` hand-draws the cable car), never in the
fuel. A stop no ride reaches either keeps a leg with no distance (`hasRoad == false`):
straight hop, road factor, "No drivable route", and nothing asks again for 30 days.

## Elevation

Google's Elevation API forbids storing results, so height comes from Open-Meteo.

Two different numbers, deliberately kept apart:

- **`Stop.elevation`** is how high the stop is: one point, stable, shown beside the
  name as a small mountain badge, the way a village sign gives it.
- **Per-leg ascent/descent** is a costing input for the fuel estimate and is **never
  displayed**: next to a place name it reads as that place's altitude.

Per-leg climb is fragile. Cumulative ascent does not converge (sample a 215 km route 16×
finer and a 10 m threshold grows it 47%), and a DEM reports the mountain above a tunnel.
`Elevation.profile` therefore samples up to 100 points spread evenly by *distance*
(not by vertex, which would crowd the hairpins), clamps each step to an 8% road grade,
then applies 50 m hysteresis. That is stable to about 6% across sampling densities.
Treat it as an order of magnitude.

## Live distance from here (`domain/RouteTracker`, `domain/DriveFromHere`)

On an ACTIVE trip the NowCard replaces the planned leg with a live route from the last
GPS fix to the stop you are heading for, with the arrival time. Every fix, from the
service or from the screen, goes to the app's one `RouteTracker`, which buys the route
via `MapProvider.drive` and publishes it as `RouteTracker.state.drive`. Never stored: it
is true for one fix. `LiveDrive` throttles it, keyed on where the last route was
*asked* from and to whatever came of it: no refetch under 2 km of movement, nothing
shown within 150 m, and a failed ask is not repeated per fix. Readers compare the
drive's target with the heading's target so a stale answer is never shown against a
different stop. The location permission is asked for on tap, never on opening a trip.

## Tracking the way driven (`location/TrackingService`, `domain/Tracks`)

- The **heading** is the stop an ACTIVE trip is driving to (`CurrentStop.heading`);
  mid-stay there is none. It is **underway** only from the trip's start date on.
- While a heading is underway, a foreground service (type `location`) takes a fix every
  2 minutes at HIGH_ACCURACY (a road trace needs GPS; the cadence duty-cycles it), drops
  fixes worse than 250 m (a cell fix), appends a fix that moved ≥ 30 m to `Stop.track`
  atomically, and keeps an ongoing notification with the distance left.
- Started only while the app is open (`TrackingTrigger`, and the NowCard when the
  permission lands). Android 12+ refuses a location foreground service started from the
  background, so there is no `ACCESS_BACKGROUND_LOCATION`, no WorkManager, no boot
  receiver, and the service is `START_NOT_STICKY`. **A drive on a day the app is never
  opened is not recorded.** The service stops itself when nothing is underway.
- A track of two or more points is drawn instead of the leg; the drive underway is the
  track so far plus the rest of the road from the last fix, dashed. An arrival fix
  (within 150 m) is never recorded, or the evening fix at the campsite would turn the
  routed road into a straight line.
- `Tracks.settle` runs after every stop write in both repositories: fixes belong to the
  drive underway, whichever stop it now arrives at.
- **Never write a `Stop` you did not just read.** A whole-stop write carries the track
  it loaded; every writer re-reads first, or a second device would lose fixes.
- About one Routes call per fix on the road, roughly 30 per hour, inside the Essentials
  free tier for one user.

## Arriving is automatic (`domain/Stay`)

Ten minutes of fixes within 500 m of the heading stop's own pin, none more than three
fix intervals apart (so a drive-by and a drive-back are not a stay), on or after the
day it is planned for, checks it in. Only a fix 1 km away resets the clock, so a pitch
on the edge of the site does not. The check-in is stamped with the *first* of those
fixes (`arrivalTime`; `arrivalDate` is the day), and the plan behind is shifted by the
lateness. `Stay.checkIn` is the same write as ✓ Arrived and touches PLANNED stops only,
so a tap and the tracker racing each other leave the first stamp standing.

Hence the service's location request has **no distance filter** (a parked phone must
keep delivering) and the accuracy filter matters (a cell fix would reset the dwell). A
stay's check-in leaves no heading, so the service stops; a zero-night stop hands the
heading on and the dwell starts over. **Undo check-in** holds the tracker off that stop
until a fix has left it (in memory: a restart at the same spot checks you in again). A
day early is not an arrival: tap ✓ Arrived, or the service checks you in just after
midnight. **Check out** ends a stay early: nights become what was slept and the plan
behind moves up.

## Region and naming

`RegionResolver` reverse-geocodes each located stop's canton/state and country
(`PlaceNameResolver.region`) into `Stop.region`, invalidated by coordinate like the
elevation. The repositories denormalize `TripName.region` onto the trip with the
totals: up to three regions in visiting order, falling back to countries, then
"& more". Home and skipped stops never name a tour.

## Home

`UserSettings.homeName`/`homeLocation`, picked in Settings through the same
`LocationSection` the stop editor uses; an unnamed fix or pin is reverse-geocoded into
the name, or the section would look as if nothing happened. A new plan opens with home
as its first stop and its last (`HomeStop.forNewPlan`), real HOME stops the timeline,
map, distance and fuel handle without knowing what home is. A stop added to the end of
a plan goes in front of the drive home (`HomeStop.returning`); starting a tour checks
the departure home in so the first night is what you are heading for. The HOME kind chip
in the stop editor appears once a home is set and puts the stop there.

## Shared places (`domain/SharedPlace`, `domain/ShareIntake`, `ui/share`)

The manifest accepts `ACTION_SEND text/plain` (Google Maps' share sheet) and `geo:`
`ACTION_VIEW`. No `https` filter: Google hosts those assetlinks, so a Maps link filter
could never verify, and enabling it by hand would swallow every Maps link on the phone.

`SharedPlace.parse` scans the payload as a haystack, not a URL, because Maps puts the
name on the line above the link:

- `!3d…!4d…` in the data segment is the place and beats `/@lat,lon`, which is only the
  map camera and marks the hit `approximate`.
- A place id is kept only from `query_place_id`.
- Short links (`maps.app.goo.gl`, `goo.gl`, `app.goo.gl`, `g.co`, exact host) are
  followed by `ShareLinkResolver`: one HEAD with redirects off, reading the first
  `Location` header. Userinfo and port are stripped from hosts before matching.
- Saved lists, My Maps drawings and live locations are rejected.
- A coordinate in plain text outside any link is the last resort, and whole numbers are
  never read as coordinates ("Flat 2, 14 Baker Street" would drop a pin in Gabon).

The Maps app's own links name the place but pin nothing (an ftid only). The chooser
(`AddToTripViewModel`) then looks the name up with Text Search, biased to the
approximate pin or home, and accepts exactly one hit. Because that coordinate came from
the Places API, it carries the 30-day clock into the stop editor.

`MainActivity` offers the parsed place to `ShareIntake` on the container, on a cold
start and on a new intent while running, but not on a recreate (which would file the
share twice), so it outlives the sign-in screen in Firebase mode. `AppNavHost` routes it once into `AddToTripRoute`; choosing a
trip opens `StopEditRoute` with the place arguments over the trip's timeline.

## Links back out

A stop that came from Google keeps its place id, so `MapsUri.share` can link to the
real place ("Open in Maps" and "Share" in the editor's location section). It uses the
documented `maps/search/?api=1&query=…&query_place_id=…` form: `query` is required, and
the shorter `maps/place/?q=place_id:` form does not reliably open.
