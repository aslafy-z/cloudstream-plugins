# TF1Plus

Unofficial TF1+ CloudStream provider, including TF1, TMC, TFX, TF1 Séries Films and LCI.

Requires Android 6 (API 23) or newer and a TF1+ account. Account permissions and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse channel programmes and search programme and replay metadata.
- Load programme replays, including SmartTV editorial season lists when available.
- Account settings inside CloudStream: sign in, sign out, account status and copyable diagnostics.
- AES-256-GCM encrypted credential and session storage using Android Keystore.
- DASH Widevine playback using the device's normal CDM, with licence headers restricted to the licence endpoint.
- Direct Chromecast playback through a local manifest and licence relay.
- In-memory catalogue cache; no backend, no API keys stored outside the client.

## Sign in

Open **CloudStream → Extensions → TF1+ → Settings** and enter your TF1+ account credentials.

Credentials and sessions are encrypted using Android Keystore. Saved sessions are reused and expired sessions are renewed automatically. Playback authorization failures trigger one session refresh before reporting the error. Signing in or out clears the plugin's cached playback links; signing out also removes the saved account.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **TF1+ direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network: the phone relays the manifest and licenses, while the TV downloads video segments directly. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive relay links. Some versions have an episode-index bug on later seasons. The [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) ships in the [CloudStream pre-release](https://github.com/recloudstream/cloudstream/releases/tag/pre-release). The plugin's direct action works independently of that host bug.

The official TF1+ Cast receiver is not implemented.

## Diagnostics

Open the plugin settings and choose **Plugin diagnostics**. The report covers sign-in, session renewal, catalogue loading, search, playback, licenses and Chromecast.

- Every event has a date, time, milliseconds and UTC offset.
- Only the latest 100 events are kept, in memory for the current plugin session.
- Failures include HTTP status codes when available. Credentials, account identifiers, programme titles, search queries, URLs, tokens, license bodies and exception messages are excluded.
- **Copy** copies the displayed report as plain text with real line breaks; **Clear** empties the report.

CloudStream also offers a global Logcat export under **Settings → Updates and backup → Show logcat**. The plugin report provides a smaller, structured view of its own operations.

If **Resolve replay: failed (HTTP 401/403)** appears, TF1+ rejected stream resolution. If **DRM stream handed to player** is the last event, the next investigation is CloudStream's player, DASH and Widevine processing. **Request Widevine license: failed (HTTP ...)** indicates a license request failure. Player errors may require CloudStream's Logcat export.

## Implementation

### Direct relay

The standard Cast receiver plays the same Widevine DASH stream as the local player. A temporary HTTP server on the phone serves the manifest and forwards license requests. The TV downloads video segments directly from the CDN.

1. CloudStream connects to its standard Cast receiver.
2. The plugin resolves the TF1+ replay and fetches its DASH manifest.
3. The manifest retains its CDN address in `BaseURL` and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
4. Each CDM request is forwarded to the resolved license endpoint. License headers are restricted to that exact endpoint; redirects are rejected.
5. Chromecast acquires the license and decrypts the content using its own Widevine CDM.

The relay binds to the network interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers and contain no account tokens. Request bodies are limited to 64 KiB, headers and connections are bounded, and sessions expire after six hours without requests. Ending the Cast session, changing accounts or unloading the plugin closes the server.

CloudStream shares its link cache between local playback and Cast. The plugin invalidates only its own entries when Cast sessions change. While the standard receiver is connected, relay links also carry the DRM configuration needed by the local player.

### Cast actions

**TF1+ direct Chromecast (relay)** resolves a fresh link and sends `RemoteMediaClient.load` to the standard session. The request contains one DASH media item, subtitles and the resume position. This action does not automatically play the next episode.

## Status and limitations

Sign-in, catalogue browsing, search posters and protected local replay have been tested in CloudStream 4.8.0-PRE on an Android TV 11 emulator with Widevine L3. Computer-driven tests using the production Kotlin relay and license code also played protected replay on Android TV emulators.

A Pixel 7a emulator discovered the TV, but Google's Android sender SDK rejected its missing Cast device credentials before playback. M6 reproduced the same failure. Full phone-to-TV playback through the plugin's Android Cast actions still needs validation on a certified receiver. Computer-driven tests do not establish that result.

Live TV, replay downloads and the official TF1+ Cast receiver are not supported. Background relay operation, other devices and playback after restarting the app still need device validation. Unofficial APIs, persisted-query IDs and license requirements may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon TF1Plus:testDebugUnitTest TF1Plus:make
```

Tests use synthetic data and cover authentication, session refresh, catalogue mapping, playback resolution, license endpoint isolation, Cast requests, relay access restrictions, diagnostic timestamps and Android clipboard line breaks. They do not replace device testing.

## Sources and credits

Most of the API research and replay integration work behind TF1Plus comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [TF1+ connector (`tf1plus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/tf1plus.py). Its catalogue, Gigya authentication and DASH/Widevine playback flows provided the foundation for this plugin.

The CloudStream integration is implemented in Kotlin, with plugin settings, encrypted sessions and diagnostics.

### Additional references

- [CloudStream Cast issue #3274](https://github.com/recloudstream/cloudstream/issues/3274) and [fix #3275](https://github.com/recloudstream/cloudstream/pull/3275)
- [Google Cast DRM license declarations](https://developers.google.com/cast/docs/media/streaming_protocols#drm)
- [CloudStream plugin template](https://github.com/recloudstream/TestPlugins)
- [License](../../LICENSE)
