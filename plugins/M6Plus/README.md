# M6Plus

Requires Android 6 (API 23) or newer and a recent CloudStream pre-release. Open the plugin settings to sign in with your M6+ account. Account permissions and geographic restrictions apply.

- Browse by channel and category, or search with programme posters and a catalogue fallback.
- Episodes are sorted by season, episode and part, with extracts after full programmes. Videos without episode numbers use their broadcast date, then publication date.
- Series retain their official episode numbers. Split programmes use a separate number for each video within a season; titles retain the original episode and part numbers.
- Credentials and sessions are encrypted using Android Keystore. Expired sessions are renewed automatically. Signing in or out clears this plugin's cached playback links.
- DASH playback uses the device's official Widevine license exchange and supports VTT subtitles. The plugin does not extract encryption keys.
- Catalogue metadata is cached in memory for 10 minutes; CloudStream manages the image cache.

Local mobile playback and both Chromecast modes have been tested on a physical device. Android TV remote navigation, background relay operation and playback after restarting the app still need device validation. Replay downloads are not supported.

## Chromecast

The episode menu offers two actions:

| Action | Usage |
| --- | --- |
| **M6+ direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network: the phone relays the manifest and licenses, while the TV downloads video segments directly. |
| **M6+ native Chromecast (with ads)** | Select the TV and a profile if prompted. The official M6+ player handles playback and advertisements. Stop this session before returning to the relay. |

CloudStream's standard **Cast episode** and **Cast mirror** actions have an episode-index bug on some later seasons. An [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) and a [validated test APK](https://github.com/aslafy-z/cloudstream/releases/tag/cloudstream-castfix-1) are available. The plugin's direct action works independently of that host bug.

## Diagnostics

Open the plugin settings and choose **Plugin diagnostics**. The report covers sign-in, session renewal, catalogue loading, search, playback, licenses and Chromecast.

- Every event has a date, time, milliseconds and UTC offset.
- Only the latest 100 events are kept, in memory for the current plugin session.
- Failures include HTTP status codes when available. Credentials, account identifiers, programme titles, search queries, URLs, tokens, license bodies and exception messages are excluded.
- **Copy** copies the displayed report as plain text with real line breaks; **Clear** empties the report.

CloudStream also offers a global Logcat export under **Settings → Updates and backup → Show logcat**. The plugin report provides a smaller, structured view of its own operations.

## Implementation

### Direct relay

The standard Cast receiver plays the same Widevine DASH stream as the local player. A temporary HTTP server on the phone serves the manifest and forwards license requests. The TV downloads video segments directly from the CDN.

1. CloudStream connects to its standard Cast receiver.
2. The plugin resolves the `usp_dashcenc_h264` asset and fetches its manifest.
3. The manifest retains its CDN address in `BaseURL` and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
4. Each CDM request is forwarded to DRMtoday with a fresh upfront token. A JSON `license` envelope, if present, is decoded into license bytes.
5. Chromecast acquires the license and decrypts the content using its own Widevine CDM.

The relay binds to the network interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers. URLs contain no Gigya identity or JWT. Request bodies are limited to 64 KiB, headers and connections are bounded, and sessions expire after six hours without requests. Ending the Cast session or signing out closes the server.

CloudStream shares its link cache between local playback and Cast. The plugin invalidates only its own entries when Cast sessions change. While the standard receiver is connected, relay links also carry the DRM configuration needed by the local player.

### Cast actions

**M6+ direct Chromecast (relay)** resolves a fresh link and sends `RemoteMediaClient.load` to the standard session. The request contains one DASH media item, subtitles and the resume position. This action does not automatically play the next episode.

**M6+ native Chromecast (with ads)** uses the official receiver. The plugin renews the Gigya identity, fetches profiles and prompts for a selection when needed. The signed identity stays in memory. The official player handles playback and advertisements. The standard receiver is restored when the session ends.


## Development

From the repository root:

```sh
./gradlew --no-daemon M6Plus:testDebugUnitTest M6Plus:make
```

Tests use synthetic data and cover authentication, catalogue mapping, episode ordering, playback resolution, license handling, Cast requests, relay access restrictions, diagnostic timestamps and Android clipboard line breaks. They do not replace device testing.

## References

An independent Kotlin implementation of the protocol described by [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/6play.py). Unofficial APIs may change; API clients are isolated in `src/main/kotlin/fr/m6plus/api`.

- [CloudStream Cast issue #3274](https://github.com/recloudstream/cloudstream/issues/3274) and [fix #3275](https://github.com/recloudstream/cloudstream/pull/3275)
- [Google Cast DRM license declarations](https://developers.google.com/cast/docs/media/streaming_protocols#drm)
- [Shaka Player DASH content protection](https://github.com/shaka-project/shaka-player/blob/main/lib/dash/content_protection.js)
- [License](../LICENSE)
