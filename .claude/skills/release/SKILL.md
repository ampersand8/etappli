---
description: Build a signed release APK for installing on a real phone. Use when asked for a release build, a shareable/installable APK, or to put the app on the phone permanently.
disable-model-invocation: true
---

# Release build

## 1. Keystore (one-time)

Check `keystore.properties` at the repo root. If missing, create the keystore and properties, both gitignored (add to `.gitignore` if not yet listed):

```bash
keytool -genkeypair -v -keystore release.keystore -alias camper \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <ask-user-or-generate> -dname "CN=CamperExperience"
```

`keystore.properties`:
```properties
storeFile=../release.keystore
storePassword=…
keyAlias=camper
keyPassword=…
```

Never commit the keystore or passwords. If generating a password, show it to the user once and tell them to save it — losing it means losing the ability to update the installed app.

## 2. Signing config (one-time)

If `app/build.gradle.kts` has no `signingConfigs`, add one that loads `keystore.properties` (skip gracefully when the file is absent so CI/other machines still build debug), and set `buildTypes.release.signingConfig = signingConfigs.getByName("release")`. Leave `isMinifyEnabled = false` unless asked — R8 needs a rules pass for Firestore/MapLibre before enabling.

## 3. Important: Firebase sign-in needs the release SHA-1

Google Sign-In will fail on release builds until the release certificate's SHA-1 is added in Firebase console → Project settings → Android app → Add fingerprint:

```bash
keytool -list -v -keystore release.keystore -alias camper | grep SHA1
```

Tell the user this step — it's console-side, only they can do it. Afterwards a fresh `google-services.json` is NOT required (fingerprints propagate server-side).

## 4. Build and deliver

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Install directly with
`adb install -r …` if a phone is connected, and send the APK file to the user.
Bump `versionCode`/`versionName` in `app/build.gradle.kts` when the previous release
is already installed somewhere.
