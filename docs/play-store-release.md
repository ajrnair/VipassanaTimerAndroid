# Getting this on Google Play

Written 29 August 2026, before the first submission. Everything the app needs
technically is done; what remains is an account, a key, and a listing.

## What already exists

- `:app:assembleRelease` produces a minified R8 build that runs (smoke-tested
  on an emulator, serialization intact under minification).
- Signing is wired: drop a `keystore.properties` beside the project and the
  release build signs itself. Neither the file nor `*.jks` is committed.
- CI runs core tests and a debug assembly on every push.
- No network permission is declared at all, which makes the data-safety form
  trivially honest.

## 1. The developer account (one-time, ~$25)

Register at [play.google.com/console](https://play.google.com/console). A
one-time 25 USD fee, payable by card. Personal accounts created after
November 2023 face an extra hurdle worth knowing before you start: **12
testers for 14 continuous days** before production access is granted. An
organisation account skips that, but needs a D-U-N-S number.

Identity verification (address, phone, sometimes ID) happens here and can
take a couple of days. Start it early — it is the longest pole in this list.

## 2. The upload key (one-time, irreplaceable)

    keytool -genkey -v -keystore upload-keystore.jks -alias upload \
      -keyalg RSA -keysize 2048 -validity 10000

Then copy `keystore.properties.example` to `keystore.properties` and fill in
the four values. **Back up both the `.jks` and its passwords somewhere you
will still have in five years.** Losing the upload key means asking Google to
reset it — recoverable, but slow and humiliating.

Google Play App Signing is on by default: Google holds the *app* signing key
and your upload key only proves it is you. That is the good arrangement;
accept it.

## 3. The bundle, not an APK

Play wants an `.aab`:

    ./gradlew :app:bundleRelease

Output: `app/build/outputs/bundle/release/app-release.aab`. The `versionCode`
must increase with every upload; `versionName` is what people read.

## 4. The listing

Prepared answers, consistent with the iOS listing and the website:

- **App name:** A Place to Sit
- **Short description (80 chars):** A privacy-first offline timer for
  Vipassana practice. No account, no tracking.
- **Full description:** the landing page's rows, in Play's plainer voice:
  Silent, Guided, Aware, the log, and the offline/no-account facts.
- **Category:** Health & Fitness
- **Content rating:** complete the questionnaire; this app answers "no" to
  everything and lands at Everyone.
- **Privacy policy URL:** https://aplacetosit.in/privacy.html
- **Graphics needed:** an app icon (512×512 PNG), a feature graphic
  (1024×500), and at least two phone screenshots. The emulator screenshots in
  this repository's history are the right shape; a proper icon is still to
  be drawn.

## 5. Data safety (the easy part)

Declare: **no data collected, no data shared.** The app has no network
permission, so the claim is verifiable by anyone reading the manifest — the
same argument the iOS privacy answers make.

Permissions to explain if asked:
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` keeps the gongs sounding while the screen
is locked; `WAKE_LOCK` keeps the timeline running through a silent sitting;
`POST_NOTIFICATIONS` is only for the ongoing "sitting in progress" notice
that a foreground service requires.

## 6. Testing tracks, then production

1. **Internal testing** — up to 100 testers, available immediately, no review
   delay. Use this to put the release build on real phones.
2. **Closed testing** — where the 12-testers-for-14-days requirement is
   satisfied, if your account has it.
3. **Production** — review typically takes a few days for a first submission,
   faster for updates.

## 7. Before the first upload

- [ ] A real launcher icon (the current one is a placeholder gong mark).
- [ ] Audio verified on a physical Android phone, screen locked: silent
      sitting gongs, a guided program, and an Awareness interval.
- [ ] Decide whether Awareness ships in the first release, as on iOS.
- [ ] Set `versionName` to something honest for a first release (1.0.0) and
      `versionCode` to 1.
- [ ] Read the target-API policy: Play forces a target-SDK bump roughly
      yearly, so this app will need at least one maintenance release a year
      even if nothing else changes.
