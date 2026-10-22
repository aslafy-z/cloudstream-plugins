# TV5MONDEPlus

Unofficial TV5MONDEplus provider for French-language films, series and documentaries.

Requires Android 5 (API 21) or newer. Programme rights and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse editorial shelves and search programmes with French metadata and posters.
- Load series seasons and episodes with official numbering.
- Play DASH streams with Widevine through Android's CDM.
- Preserve manifest audio and subtitle tracks for the player.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. The plugin obtains a fresh anonymous session when resolving a replay. Session tokens are not persisted.

## Chromecast

Chromecast is not enabled in this version. Receiver playback and license handling have not been validated.

## Diagnostics

API failures report HTTP status codes. Missing streams and catalogue-query errors produce explicit messages. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Catalogue shelves use TV5MONDEplus's Red Bee configuration and asset APIs. Programme details, seasons and episodes use the website's persisted GraphQL queries. Anonymous authorization and replay entitlement requests use the official TV5MONDEplus API, without overriding the viewer's country.

The client selects Widevine-compatible DASH instead of assuming the first returned format is playable. Android's CDM acquires the license from the supplied endpoint. The plugin does not extract encryption keys or forward the anonymous bearer token to media or license servers.

## Status and limitations

Live API checks verified catalogue references, search, film details, a series season, anonymous authentication and manifest access. Search, film details and visible Widevine-protected playback of **Adamo, le film de sa vie** were verified inside CloudStream on a Pixel 7a emulator, including visible subtitles.

Physical-device behavior, Chromecast, live TV and downloads are not validated. Live TV and downloads are not offered. Search returns the API's initial result page. Individual editorial shelves expose their initial asset selection. An incomplete season produces an explicit error rather than silently hiding episodes. Persisted query identifiers may need updating when the website changes.

## Development

From the repository root:

```sh
./gradlew --no-daemon TV5MONDEPlus:testDebugUnitTest TV5MONDEPlus:make
```

Tests cover French metadata, image selection, programme identifiers, episode numbers, Widevine format selection and the catalogue gateway's required query serialization. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [TV5MONDEplus connector (`tv5mondeplus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/wo/tv5mondeplus.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [TV5MONDEplus](https://www.tv5mondeplus.com/)
- [License](../../LICENSE)
