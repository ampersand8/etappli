# Google Maps setup (optional)

Without a key the app runs on MapLibre + OpenFreeMap with Photon search, exactly as
before. Add a key and both the map and place search switch to Google.

## Console steps

1. https://console.cloud.google.com → new project (or reuse the Firebase one).
2. **Billing must be enabled**, even inside the free tier. The Android Maps SDK has no
   per-load charge, but Google requires a card on file.
3. APIs & Services → Library → enable **Maps SDK for Android** and **Places API (New)**
   (the one called "Places API (New)", not the legacy "Places API").
4. APIs & Services → Credentials → Create credentials → API key. Then restrict it:
   - *Application restrictions* → Android apps → add package `com.nuelto.camperexperience`
     with your debug SHA-1 (and the release one when you have it):
     ```bash
     ./gradlew :app:signingReport
     ```
   - *API restrictions* → Restrict key → Maps SDK for Android + Places API (New).
5. Put it in `local.properties` (gitignored, same file as `webClientId`):
   ```properties
   mapsApiKey=AIza...
   ```

A new key can take a few minutes to activate. A grey map is almost always a key problem:

```bash
~/Android/Sdk/platform-tools/adb logcat -s "Google Maps Android API"
```

That prints the package and SHA-1 the SDK actually presented — compare it with what the
console has. Note the Firebase console registration does **not** carry over; this is a
separate credential.

CI never sees a key (`local.properties` is gitignored), so CI only ever exercises the
MapLibre path. The Google path is emulator/device-only.

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

§14.2 also forbids showing Places results "in conjunction with a non-Google map", which
is why `GooglePlacesSearch` is reachable only from `GoogleMapProvider`. In MapLibre mode
search stays on Photon/OSM.

## Known gap

Google requires their logo alongside their content; Settings currently shows a text
attribution only. Worth closing before this leaves the branch.
