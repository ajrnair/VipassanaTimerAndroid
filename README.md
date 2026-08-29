# A Place to Sit — Android

A privacy-first offline timer for Vipassana practice. The Android sibling of
[the iPhone app](https://github.com/ajrnair/VipassanaTimerApple), sharing its
contracts rather than its code: the same assembled guided-audio programs, the
same gong recordings, the same JSON history schema, and a Kotlin port of the
same timer core held to the same tests.

Early. Not yet released.

- `core/` — the pure timer engine (JVM, no Android), tested.
- `app/` — Jetpack Compose UI and the foreground playback service.

No account, no tracking, no analytics, no network. MIT licensed; the bundled
audio has its own terms — see the iOS repository's ASSET_LICENSES.
