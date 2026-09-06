# Etappli developer docs

What the app does, why it is built the way it is, and the rules a change must respect —
the material a senior developer needs to pick the project up. Setup how-tos stay at the
repo root and are not repeated here:

| Root doc | Use it for |
| --- | --- |
| [README.md](../README.md) | Build, install on a phone, release bundle |
| [FIREBASE_SETUP.md](../FIREBASE_SETUP.md) | Firebase project, Google Sign-In, Firestore rules |
| [GOOGLE_MAPS_SETUP.md](../GOOGLE_MAPS_SETUP.md) | Maps Platform key, API restrictions, the 30-day coordinate rule |
| [PLAY_STORE_SETUP.md](../PLAY_STORE_SETUP.md) | Upload key, Play Console steps, data-safety answers |
| [PRIVACY.md](../PRIVACY.md) | The published privacy policy |
| [CLAUDE.md](../CLAUDE.md) | Condensed working reference for AI-assisted sessions |

## Reading order

1. [Business](01-business.md) — what Etappli is for, the core concepts, the user journeys, the product principles.
2. [Domain model](02-domain-model.md) — entities, lifecycles, invariants, and how they are stored in Firestore.
3. [Architecture](03-architecture.md) — layers, composition root, repositories, ViewModels, navigation, the map seam, key sequences.
4. [Costing](04-costing.md) — recorded costs versus estimates, the fuel model, vignettes.
5. [Location and routing](05-location-and-routing.md) — Places, Routes, elevation, live distance, drive tracking, automatic check-in, shared places.
6. [Toolchain and build](06-toolchain-and-build.md) — stack, configuration inputs, runtime modes, versioning, CI, manifest.
7. [Testing](07-testing.md) — the two gates, test types, fakes, what only a device can verify.
8. [Conventions](08-conventions.md) — the hard rules, recipes for common changes, where to look for what.

## Glossary

| Term | Meaning |
| --- | --- |
| **Etappli** | Swiss-German diminutive of *Etappe*: one stage or leg of a journey. |
| **Tour / plan** | A trip that has not happened yet (`TripStatus.PLANNED`). "Plan a tour" creates one. |
| **Trip** | The generic record, any status. "Log a trip" records one that already happened. |
| **Stop** | A place on a trip with an order, an arrival date and a number of nights. |
| **Stay** | A stop you sleep at: campsite, Stellplatz, free camp (`StopKind.isStay`). |
| **Stellplatz** | German for a motorhome parking pitch, usually cheaper and simpler than a campsite. |
| **Free camp** | Overnighting somewhere unofficial or free; a stay with no price by nature. |
| **Visit** | A zero-night stop you drive through; a route point, not a stay. |
| **Home** | A stop of kind HOME at the start and end of a plan, taken from Settings. |
| **Leg** | The drive arriving at a stop, as Google routed it (`StopLeg`). |
| **Track** | The GPS fixes actually recorded while driving to a stop (`Stop.track`). |
| **Heading** | The stop an active trip is currently driving to. |
| **Underway** | A heading on or after the trip's start date; only then is anything recorded. |
| **Dwell** | The run of fixes near the heading stop that turns into an automatic check-in. |
| **Gap row** | A run of unplanned nights between two stops, derived from dates and never stored. |
| **Park and ride** | A leg to a stop no road reaches: drive to where the last ride boards, then ride. |
| **Vignette** | A prepaid motorway sticker or e-permit (Switzerland, Austria, Slovenia, Czechia). |
| **≈** | Prefix on any number that contains an estimate rather than a recorded cost. |
| **SST §14.3 / §19.3** | Google Maps Platform Service Specific Terms: 30-day retention for Places and Routes coordinates. |
