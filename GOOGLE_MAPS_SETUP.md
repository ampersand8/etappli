# Google Maps setup (optional)

Without a key the app runs on MapLibre + OpenFreeMap exactly as before. Add a key and
the map switches to Google Maps; place search stays on Photon/OpenStreetMap (see
"Why search stays on OSM" below).

## Console steps

1. https://console.cloud.google.com → new project (or reuse the Firebase one).
2. **Billing must be enabled**, even inside the free tier. The Android Maps SDK has no
   per-load charge, but Google requires a card on file.
3. APIs & Services → Library → enable **Maps SDK for Android**.
4. APIs & Services → Credentials → Create credentials → API key. Then restrict it:
   - *Application restrictions* → Android apps → add package `com.nuelto.camperexperience`
     with your debug SHA-1 (and the release one when you have it):
     ```bash
     ./gradlew :app:signingReport
     ```
   - *API restrictions* → Restrict key → Maps SDK for Android.
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

## Why search stays on OSM

Google's Places Service Specific Terms, §14.3, verbatim:

> Customer may temporarily cache latitude and longitude values from the Places API for
> up to 30 consecutive calendar days, after which Customer must delete the cached
> latitude and longitude values.

This app stores each stop's coordinate in Firestore for the life of the trip and redraws
it years later, so Places coordinates cannot be the source. §14.2 also forbids showing
Places results "in conjunction with a non-Google map", which would tie search and map
together. Photon/OSM has neither restriction, so `GoogleMapProvider.placeSearch()`
deliberately returns the Photon implementation.
