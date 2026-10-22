# TF1Plus (CloudStream)

Unofficial TF1+ CloudStream provider, including TF1, TMC, TFX, TF1 Séries Films and LCI.

Features:
- Browse channel programmes and search programme and replay metadata.
- Load programme replays, including SmartTV editorial season lists when available.
- Account settings inside CloudStream: sign in, sign out, account status and copyable diagnostics.
- AES-256-GCM encrypted credential and session storage using Android Keystore.
- DASH Widevine playback using the device's normal CDM, with licence headers restricted to the licence endpoint.
- In-memory catalogue cache; no backend, no API keys stored outside the client.

## Sign in
Install the plugin and open CloudStream > Extensions > TF1+ > Settings.
Enter the credentials for an existing TF1+ account. Login happens over HTTPS.
Credentials and the playback token are encrypted with Android Keystore. A valid session
is reused after restarting CloudStream and renewed when it expires. Playback authorization
failures trigger one session refresh before the error is reported. Signing out clears the
saved account and cached playback links.

## Diagnostics

Open **CloudStream > Extensions > TF1+ > Settings > Plugin diagnostics**.

- Reproduce the problem (search, open a programme, select an episode, tap Play).
- Use **Copy** to copy the diagnostic report, then attach it to a GitHub issue.
- Use **Clear** to reset the in-memory event history before reproducing.
- Each event has a timestamp and an operation label. HTTP failures include numeric status codes and no request URLs.
- The latest 100 events are kept only in memory for the plugin session.
- Identifiers, credentials, JWTs, Gigya signatures, license challenges, request/response bodies, URLs and arbitrary exception messages are never added.

Interpretation: if **Resolve replay: failed (HTTP 401/403)** appears, TF1+ rejected stream resolution.
If **DRM stream handed to player** is the last event, the plugin supplied a link and the next investigation is CloudStream's player, DASH and Widevine processing.
If **Request Widevine license: failed (HTTP ...)** appears, the license server rejected a request.
Note that device/player errors may occur outside the plugin and require CloudStream's own Logcat export.

## Status / limitations
This provider is experimental and is not affiliated with TF1. GraphQL persisted-query IDs, login flows and stream
licence headers are subject to upstream changes. Use a valid account, compatible Widevine
device and an authorised region. No DRM circumvention or content downloads.
Chromecast and live TV are not implemented.
Sign-in, catalogue browsing, search posters and protected replay playback have been tested
in CloudStream 4.8.0-PRE on an Android TV 11 emulator with Google Widevine L3.
CI covers payload mapping, session reuse and refresh, diagnostics, and licence endpoint
isolation. Other devices and their Widevine security levels still require playback testing.

## Development
Run from the repository root:

```sh
./gradlew --no-daemon TF1Plus:testDebugUnitTest TF1Plus:make
```

## Sources and credits

Most of the API research and replay integration work behind TF1Plus comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [TF1+ connector (`tf1plus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/tf1plus.py). Its catalogue, Gigya authentication and DASH/Widevine playback flows provided the foundation for this plugin.

The CloudStream integration is implemented in Kotlin, with plugin settings, encrypted sessions and diagnostics.

- [CloudStream plugin template](https://github.com/recloudstream/TestPlugins)
- [License](../../LICENSE)
