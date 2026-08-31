# Google Maps setup

The app needs a Google Maps Platform key. Without one it still builds, installs and runs
— trips, stops, costs, GPS — but there is no map and no place search.

## Console steps

1. https://console.cloud.google.com → new project (or reuse the Firebase one).
2. **Billing must be enabled**, even inside the free tier. The Android Maps SDK has no
   per-load charge, but Google requires a card on file.
3. APIs & Services → Library → enable **Maps SDK for Android**, **Places API (New)**
   (the one called "Places API (New)", not the legacy "Places API") and **Routes API**.
4. APIs & Services → Credentials → Create credentials → API key. Then restrict it:
   - *Application restrictions* → Android apps → add package `com.nuelto.camperexperience`
     with your debug SHA-1 (and the release one when you have it):
     ```bash
     ./gradlew :app:signingReport
     ```
   - *API restrictions* → Restrict key → Maps SDK for Android + Places API (New) +
     Routes API.
5. Put it in `local.properties` (gitignored, same file as `webClientId`):
   ```properties
   mapsApiKey=AIza...
   ```

## Application restrictions and the web-service calls

One key covers all three APIs — verified against a live `computeRoutes` call. Enabling
Routes API and listing it under *API restrictions* is the whole requirement.

The catch is the **application** restriction in step 4. Maps SDK for Android is an SDK and
proves its identity by itself, but Places API (New) and Routes API are called here as
plain web services over `HttpURLConnection`. Google's rule for those is that an
Android-restricted key requires `X-Android-Package` and `X-Android-Cert` (SHA-1 as
undelimited hex) headers on every request — which this app does not send, for either API.

So the key currently works because it carries no Android application restriction. Add one
and **both** place search and routing stop working, not just routing. If you want that
restriction, the fix is those two headers in `location/GooglePlacesSearch` and
`location/RouteServices`, not a second key.

Either way, cap the spend: APIs & Services → Quotas → Routes API, and set a daily ceiling
you would not mind paying. Compute Routes Essentials gives 10,000 free requests a month
and this app spends roughly one per trip edit.

**Without the Routes API enabled** the app still runs: routes fall back to straight lines
between stops, and distance to straight line × the road factor in Settings.

## Elevation is not Google

Heights come from Open-Meteo (Copernicus DEM GLO-90), not Google's Elevation API, whose
policies forbid caching or storing results at all — and a height that has to be re-fetched
to draw a list is no use. Open-Meteo needs no key; the Copernicus licence asks for
attribution, which is the second half of the line at the bottom of Settings.

A DEM gives ground level, which is the road only where the road is on the ground. Through
a tunnel it returns the mountain above, so the per-leg climb clamps every step to a grade
a road could actually hold before summing it. The stop's own height needs none of that —
one point, no accumulation — which is why it is the number worth showing.

A new key can take a few minutes to activate. A grey map is almost always a key problem:

```bash
~/Android/Sdk/platform-tools/adb logcat -s "Google Maps Android API"
```

That prints the package and SHA-1 the SDK actually presented — compare it with what the
console has. Note the Firebase console registration does **not** carry over; this is a
separate credential.

CI never sees a key (`local.properties` is gitignored). Tests render through
`PlaceholderMapProvider`, so they pass regardless; the real map is emulator/device-only.

## The 30-day coordinate rule

Google's Places Service Specific Terms, §14.3, verbatim:

> Customer may temporarily cache latitude and longitude values from the Places API for
> up to 30 consecutive calendar days, after which Customer must delete the cached
> latitude and longitude values.

This app keeps a stop's coordinate for the life of the trip, so the rule is enforced
rather than ignored:

- A stop picked from a Google Places hit stores `placeId` (which the terms let you keep
  indefinitely) plus `locationCachedAt`.
- `domain/PlaceCache` decides when a coordinate is past its 30 days.
- `domain/PlaceCacheSweeper` runs when a trip is opened: it re-fetches the coordinate
  through Place Details where it can, and **deletes it** where it can't.
- Coordinates from a GPS fix, the crosshair or an OSM search have no `locationCachedAt`
  and never expire.

**What this costs you:** a Places-sourced stop older than 30 days loses its pin until the
app is next opened online. Trips shorter than a month are unaffected during and just
after the trip; older trips need one online visit to redraw. That is the trade this
design accepts — the alternative was not storing Google coordinates at all.

The Routes API sits under the same clock: §19.3 grants it the same 30-day window that
§14.3 grants Places, and a stored leg is a sequence of coordinates. `domain/RouteCache`
decides when one is past its days or no longer describes the drive it was fetched for,
and `domain/RouteRefresher` re-fetches where it can and deletes where it can't. A leg
that goes is not a loss: the map falls back to a straight line and the estimate to the
road factor.

§14.2 also forbids showing Places results "in conjunction with a non-Google map" — moot
now that Google is the only map, but it is why search and map cannot be mixed providers.

## Known gap

Google requires their logo alongside their content; Settings currently shows a text
attribution only. Worth closing before this leaves the branch.
