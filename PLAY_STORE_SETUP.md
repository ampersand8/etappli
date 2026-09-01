# Publishing to Google Play

The repo side is done: release signing is wired up, the release bundle builds, and the
listing text and graphics are in `play/listing/`. What is left needs your Play Console
account, so it is on you — the steps are below in order.

## 1. Create the upload key (once)

The keystore holds a password, so it is yours to create and yours to keep. **Keep it out
of this directory**: everything here is one `git clean -xdf` away from being deleted, and
the repo is public, so a slip in `.gitignore` would publish the key. Put it somewhere
that gets backed up:

```bash
mkdir -p ~/keystores
```

```bash
keytool -genkeypair -v -keystore ~/keystores/camperexperience-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

keytool writes the keystore only at the very end, so if that directory is missing it
asks every question first and then dies with `FileNotFoundException`.

Then add the four values to `~/.gradle/gradle.properties` — outside the repo, and picked
up by every build automatically:

```properties
camperUploadStoreFile=~/keystores/camperexperience-upload.jks
camperUploadStorePassword=<the password you chose>
camperUploadKeyAlias=upload
camperUploadKeyPassword=<the same password, unless you set a separate key password>
```

Back up both the `.jks` and the password (password manager, encrypted backup). Losing
the keystore means asking Google to reset your upload key; leaking it lets someone else
sign updates to your app.

A gitignored `keystore.properties` in the project root, with the same four keys minus
the `camperUpload` prefix, still works if you prefer it — the build checks
`~/.gradle/gradle.properties` first and falls back to it. Without either, the release
build still works; it just comes out unsigned, which Play rejects.

## 2. Build the bundle

```bash
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. `versionCode` is the git
commit count, so it rises by itself; Play refuses a versionCode it has already seen.

## 2b. Finish the new Firebase project (one-off)

A fresh Firebase project **`etappli`** (project number `449888443743`) was created on
2026-09-01 to match the new name, replacing `camperexperience`. Already done:

- Android app `com.nuelto.etappli` registered (app id
  `1:449888443743:android:05a59a2bbaed21c117aaea`).
- Debug SHA-1 `44:91:E0:59:B2:A8:33:78:A5:F4:43:E4:78:11:8E:67:61:1B:96:4F` added.
- `.firebaserc` now points at `etappli`.

Three things need the console, because the APIs behind them refuse without billing or
have no CLI surface:

1. **Create the Firestore database.** Firebase Console → `etappli` → Firestore Database →
   Create database → **Standard edition**, location **eur6 (Zurich)**, start in
   production mode. The console can do this on the free Spark plan; the Admin API cannot.
2. **Enable Google Sign-In.** Authentication → Sign-in method → Google → enable, and set
   a project support email. Until this is done the project has **no OAuth clients at
   all**, which is why `app/google-services.json` has deliberately not been written yet —
   the app would show a sign-in screen that cannot succeed. It runs in local demo mode
   meanwhile.
3. **Publish the security rules.** `firestore.rules` in this repo is the intended
   content; paste it into Firestore → Rules and publish.

Then download `google-services.json` (Project settings → Your apps → Android) into
`app/`, and copy the **web** client id out of it — the `oauth_client` entry with
`"client_type": 3` — into `webClientId` in `gradle.properties`. The value there now
(`380057794405-…`) belongs to the old project and will not work.

## 3. Create the app in Play Console

Play Console → **Create app**: name `Etappli`, English (or German) as default
language, **App**, **Free**, and accept the declarations. This claims the package name
`com.nuelto.etappli`.

## 4. Fill in App content

Under **Policy → App content**. All of these are required before any track can go live:

| Item | Answer for this app |
| --- | --- |
| Privacy policy | `https://github.com/ampersand8/etappli/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | Sign-in required — give the reviewers a test Google account, or move the app to a closed/internal track where no review happens |
| Content rating | Fill the questionnaire; a trip log rates as *Everyone* |
| Target audience | 18+; not designed for children |
| Data safety | See the table below |
| Data deletion | Required, because the app has accounts — see the warning below |
| Government apps / financial features / health | No to all |

### Data safety answers

| Data type | Collected | Shared | Required | Purpose |
| --- | --- | --- | --- | --- |
| Email address | Yes (Google Sign-In) | No | Yes | Account management |
| User IDs | Yes (Firebase uid) | No | Yes | Account management |
| Approximate / precise location | Yes, only on explicit tap | No | No | App functionality |
| Other user-generated content (trips, stops, notes, costs) | Yes | No | Yes | App functionality |

Encrypted in transit: **yes**. Users can request deletion: **yes**. No data is used for
advertising, analytics or tracking, and the app has no analytics SDK.

## 5. Upload

**Internal testing** (recommended first): Test and release → Testing → Internal testing →
Create new release → upload the `.aab`. Google offers to manage app signing — accept it.
No review, live in minutes, up to 100 testers, and it registers the package name and the
signing key, which is what the September 2026 deadline is about.

**Production** additionally needs the store listing (paste from
`play/listing/store-listing.md`, upload the graphics from `play/listing/`) and passes a
review that can take days.

## 6. After the first upload — two things will otherwise be broken

1. **Google Sign-In.** The installed app is signed by Google's app signing key, not your
   upload key, so its fingerprint is unknown to Firebase and sign-in fails. Copy the
   **SHA-1 of the app signing certificate** from Play Console → Test and release → Setup
   → App signing, add it in Firebase Console → Project settings → Your apps →
   *Add fingerprint*, then re-download `app/google-services.json`.
2. **The Maps API key ships inside the bundle.** It is unrestricted, because Places and
   Routes are called as plain web services and an Android app restriction would break
   them. Anyone can extract it from a public download and spend your quota. Before going
   to production, at minimum restrict the key to the four APIs it needs (Maps SDK for
   Android, Places API (New), Routes API) and set a billing budget alert and per-API
   quota caps in Google Cloud Console.

## Developer verification (the deadline in the mail)

Registering the package name is what the 30 September 2026 requirement asks for, and
step 5 does it. Play Console → **Android developer verification** lists your package
names and their status; check `com.nuelto.etappli` shows there afterwards, and
finish any package-name drafts left half-done. Apps you distribute outside Play are
registered on the same page, with the signing key you use for them.
