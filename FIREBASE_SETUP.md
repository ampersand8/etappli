# Firebase setup (one-time, ~10 minutes)

The app builds and runs in **local-only mode** (in-memory demo data) until you complete
these steps. Once `google-services.json` is in place, rebuild and the app switches to
Google Sign-In + Firestore sync automatically.

## 1. Create the Firebase project

1. Go to <https://console.firebase.google.com> → **Add project** (e.g. `camperexperience`).
   Google Analytics can be disabled.
2. In the project: **Add app → Android**.
   - Package name: `com.nuelto.etappli`
   - Debug signing SHA-1 (required for Google Sign-In). Get it with:
     ```bash
     ./gradlew signingReport
     ```
     Copy the `SHA1:` line of the `debug` variant.
3. Download **`google-services.json`** and put it at `app/google-services.json`
   (it is gitignored).

## 2. Enable Google Sign-In

1. **Build → Authentication → Get started → Sign-in method → Google → Enable**.
2. Under the provider settings, copy the **Web client ID**
   (ends in `.apps.googleusercontent.com`).
3. Add it to `local.properties` (gitignored) or `gradle.properties` (committed —
   fine too, the ID ships inside the APK anyway and is not a secret):
   ```properties
   webClientId=1234567890-xxxxxxxx.apps.googleusercontent.com
   ```

## 3. Create the Firestore database

1. **Build → Firestore Database → Create database**.
   Choose **production mode** and a region close to you (e.g. `europe-west6`, Zürich).
2. In the **Rules** tab, paste the contents of [`firestore.rules`](firestore.rules)
   and publish.

## 4. Rebuild and verify

```bash
./gradlew :app:installDebug
```

- The app now starts with a **Sign in with Google** screen.
- Create a trip; it appears in the Firestore console under `users/{your-uid}/trips`.
- Airplane-mode test: edits made offline sync automatically when the network returns.
- Reinstall test: uninstall, reinstall, sign in — your data comes back.

No composite indexes are needed. If a future query ever requires one, Firestore logs
a direct "create index" link in logcat.
