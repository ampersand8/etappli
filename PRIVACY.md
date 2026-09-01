# Privacy Policy — Etappli

Last updated: 1 September 2026

Etappli ("the app") is a personal trip log for camper travel. This policy
describes what the app stores and who else sees it.

## What the app stores

- **Your Google account identifier and email address**, when you sign in with Google.
  Sign-in is handled by Firebase Authentication; the app itself only ever uses the
  account's user ID to keep your data separate from anyone else's.
- **The trips you enter**: trip and stop names, dates, nights, costs, expenses, notes,
  and the coordinates of the stops you pick.
- **Your settings**: currency, fuel consumption and price, vehicle mass, and your home
  location if you set one.
- **Your device's location**, only at the moment you tap "I'm here" or ask how far the
  next stop is. It is used to fill in a stop's coordinates or to measure a distance.
  The app has no background location access and does not track your movement.

Everything is stored in Google Firebase (Cloud Firestore) under the developer's Firebase
project, and cached on your device so the app works offline.

## Who else receives data

- **Google Firebase** (Authentication, Cloud Firestore) — stores your account and trips.
- **Google Maps Platform** (Maps SDK, Places API, Routes API) — receives the search terms
  you type, the places you tap, and the coordinates between which a route is calculated.
- **Open-Meteo** — receives coordinates in order to return their height above sea level.

The app contains no advertising, no analytics, and no third-party trackers. Your data is
not sold and is not shared with anyone else.

## Retention and deletion

Your data is kept until you delete it. You can delete individual trips, stops and
expenses in the app at any time.

To have your account and all data associated with it erased, write to
`etappli@nuelto.com` from the address you signed in with. The account and its trips will
be deleted within 30 days.

## Children

The app is not directed at children and is not intended for use by anyone under 13.

## Changes

Material changes to this policy will be published at this address before they take effect.

## Contact

`etappli@nuelto.com`
