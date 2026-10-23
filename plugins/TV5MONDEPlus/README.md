# TV5MONDEPlus

Unofficial TV5MONDEplus provider for French-language films, series and documentaries.

Requires Android 6 (API 23) or newer. Programme rights and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse editorial shelves and search programmes with French metadata and posters.
- Load series seasons and episodes with official numbering.
- Play DASH streams with Widevine through Android's CDM.
- Preserve manifest audio and subtitle tracks for the player.
- Chromecast playback through a local manifest and license relay.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. The plugin obtains a fresh anonymous session when resolving a replay. Session tokens are not persisted.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **TV5MONDEplus direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network: the phone relays the manifest and licenses, while the TV downloads video segments directly. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive relay links. Some versions have an episode-index bug on later seasons. An [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) and a [validated test APK](https://github.com/aslafy-z/cloudstream/releases/tag/cloudstream-castfix-1) are available. The plugin's direct action works independently of that host bug.

Unprotected streams are sent to the TV directly, without the relay. Relay failures are reported as playback errors and toasts; there is no Chromecast diagnostic report.

## Diagnostics

API failures report HTTP status codes. Missing streams and catalogue-query errors produce explicit messages. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Catalogue shelves use TV5MONDEplus's Red Bee configuration and asset APIs. Programme details, seasons and episodes use the website's persisted GraphQL queries. Anonymous authorization and replay entitlement requests use the official TV5MONDEplus API, without overriding the viewer's country.

The client selects Widevine-compatible DASH instead of assuming the first returned format is playable. Android's CDM acquires the license from the supplied endpoint. The plugin does not extract encryption keys or forward the anonymous bearer token to media or license servers.

### Chromecast relay

CloudStream's standard Cast path sends the TV only a URL and content type, so the receiver cannot learn the Red Bee license endpoint. A temporary HTTP server on the phone serves the manifest and forwards license requests. The TV downloads video segments directly from the CDN, which needs no headers and allows cross-origin requests.

1. CloudStream connects to its standard Cast receiver.
2. The plugin resolves the replay and fetches its DASH manifest.
3. The manifest gains an absolute `BaseURL` pointing at the CDN and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
4. Each CDM challenge is posted unchanged to the resolved `licenseServerUrl`, which returns raw Widevine license bytes. No extra headers are sent and redirects are rejected.
5. Chromecast acquires the license and decrypts the content using its own Widevine CDM.

The relay binds to the network interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers; the license endpoint and its session parameter stay on the phone. Request bodies are limited to 64 KiB, headers and connections are bounded, at most four sessions are kept, and sessions expire after six hours without requests. Ending the Cast session or unloading the plugin closes the server.

CloudStream shares its link cache between local playback and Cast. The plugin invalidates only its own entries when Cast sessions change or a relay session is evicted. While the standard receiver is connected, relay links also carry the DRM configuration needed by the local player.

**TV5MONDEplus direct Chromecast (relay)** resolves a fresh link and sends `RemoteMediaClient.load` to the standard session. The request contains one DASH media item and the resume position. This action does not automatically play the next episode.

## Status and limitations

Live API checks verified catalogue references, search, film details, a series season, anonymous authentication and manifest access. Search, film details and visible Widevine-protected playback of **Adamo, le film de sa vie** were verified inside CloudStream on a Pixel 7a emulator, including visible subtitles.

Live API checks also confirmed that the CDN serves manifests and segments without headers and with `Access-Control-Allow-Origin: *`, and that the license endpoint accepts raw challenges and returns raw Widevine messages. Playback on a physical Chromecast is not validated.

Physical-device behavior, live TV and downloads are not validated. Live TV and downloads are not offered. Search returns the API's initial result page. Individual editorial shelves expose their initial asset selection. An incomplete season produces an explicit error rather than silently hiding episodes. Persisted query identifiers may need updating when the website changes.

## Development

From the repository root:

```sh
./gradlew --no-daemon TV5MONDEPlus:testDebugUnitTest TV5MONDEPlus:make
```

Tests cover French metadata, image selection, programme identifiers, episode numbers, Widevine format selection, the catalogue gateway's required query serialization, the Cast manifest rewrite, relay access and size limits, session eviction, license forwarding and the bounded Cast LOAD request. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [TV5MONDEplus connector (`tv5mondeplus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/wo/tv5mondeplus.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [TV5MONDEplus](https://www.tv5mondeplus.com/)
- [License](../../LICENSE)
