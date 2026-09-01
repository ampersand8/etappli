---
description: Verify the app end-to-end on the emulator — boot AVD, install, walk the core screens with screenshots, mock GPS, scan for crashes. Use after any UI, map, location, or navigation change, or when asked to "check the app still works".
---

# Verify app on emulator

Run through this sequence, screenshotting at each step and reading each screenshot to confirm the expected content. Save screenshots to the session scratchpad, not the repo.

## 1. Boot (skip if `adb devices` already shows a device)

```bash
nohup ~/Android/Sdk/emulator/emulator -avd Pixel_9a -no-snapshot-save -no-boot-anim -gpu auto > /dev/null 2>&1 &
~/Android/Sdk/platform-tools/adb wait-for-device
~/Android/Sdk/platform-tools/adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
```

The emulator command needs sandbox disabled (KVM access).

## 2. Install and launch

```bash
./gradlew :app:installDebug
~/Android/Sdk/platform-tools/adb shell am force-stop com.nuelto.etappli
~/Android/Sdk/platform-tools/adb shell am start -n com.nuelto.etappli/.MainActivity
```

Screenshot: `adb exec-out screencap -p > <scratchpad>/step.png`, then Read the file.
Screen coordinates from a Read screenshot must be multiplied by the scale factor noted
under the image before use in `adb shell input tap`.

## 3. Walk the core screens

1. **Trip list** — trips render with CHF totals. (Firebase mode with no signed-in user shows the sign-in screen instead; that's correct, note it and stop after step 5's crash scan.)
2. **Trip detail** — tap a trip: embedded map, cost breakdown card, stops, expenses.
3. **All-trips map** (map icon, top bar) — tiles render (needs network), colored markers + route lines, tap a marker → info card appears. A blank beige map = renderer problem; check logcat for `Mbgl` errors and confirm the OpenGL runtime dependency is intact.
4. **Expense sheet** — in trip detail, + next to "Expenses"; with type Fuel, expand "Estimate from distance…" and confirm the computed amount = distance × consumption / 100 × price.
5. **Stop editor + GPS** — add stop, then:
   ```bash
   ~/Android/Sdk/platform-tools/adb emu geo fix 7.4474 46.9480   # lon FIRST, then lat
   ```
   Tap "I'm here", grant the permission dialog ("While using the app"), send the geo fix again, wait ~10 s → lat/lng fields fill. Also test "Pick on map" → pan → "Use this spot" → fields fill.

## 4. Crash scan

```bash
~/Android/Sdk/platform-tools/adb logcat -d | grep -E 'FATAL|AndroidRuntime' | tail -20
```

## 5. Report

State which steps passed/failed with the screenshot evidence. Any failed step: investigate before reporting, don't just list it.
