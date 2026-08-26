---
description: Capture screenshots of every app screen in light and dark theme for a design or regression review. Use when asked for a design pass, screenshot audit, or before/after comparison.
---

# Screen audit

Prerequisite: emulator running with the app installed (see the **verify-app** skill for boot/install commands). Save all images to the session scratchpad; `adb` is at `~/Android/Sdk/platform-tools/adb`.

## Capture

For each theme:

```bash
adb shell "cmd uimode night no"    # light; use "night yes" for dark
adb shell am force-stop com.nuelto.camperexperience
adb shell am start -n com.nuelto.camperexperience/.MainActivity
```

Screens to capture (navigate by tapping; scale tap coordinates as noted under each Read screenshot):

1. Trip list
2. Trip detail (a trip with stops + expenses)
3. Expense sheet with the fuel estimator expanded
4. Trip edit
5. Stop edit
6. Location picker
7. All-trips map (wait for tiles before capturing)
8. Settings
9. Sign-in screen (only reachable in Firebase mode while signed out)

Name files `<theme>_<screen>.png`. Restore the emulator to light mode afterwards (`cmd uimode night no`).

## Review

Read every screenshot and note, per screen: unreadable contrast in dark mode, hardcoded-looking colors that ignore the theme, clipped or overlapping text, misaligned rows, missing empty states. Summarize findings ranked by severity; don't fix unless asked.
