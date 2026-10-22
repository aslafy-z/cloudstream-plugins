# FranceTV

Unofficial france.tv provider for France 2, France 3, France 4, France 5, franceinfo and France tv Slash replays.

Requires Android 5 (API 21) or newer. Programme availability, account requirements and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse channel shelves and search programmes and full replays with posters.
- Load programme seasons and paginated episode lists, preserving official episode numbers.
- Exclude live entries, extracts, unpublished videos and recommendation shelves from episode lists.
- Resolve authorized DASH and HLS streams, with a separate Widevine path for protected DASH.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

The plugin uses replay endpoints that do not require a saved account. Account sign-in is not implemented; content requiring it may be unavailable.

## Chromecast

Chromecast is not enabled in this initial version. The protected DASH path needs receiver-side license-header handling before it can be validated for Cast.

## Diagnostics

API failures report HTTP status codes. Missing streams report programme or regional availability errors. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Catalogue and search use the Yatta mobile API. Replay resolution uses `k7.ftven.fr`, then the supplied stream is authorized through `hdfauth.ftven.fr`. The service determines the region; the client does not override the country.

For protected DASH, the client requests a Widevine authorization token and attaches it to the player's license request parameters. The device's CDM performs license acquisition. The plugin does not extract encryption keys. Audio and subtitle tracks embedded in the manifest remain available to the player.

## Status and limitations

Live API checks verified channel shelves, search, all six seasons of Astrid et Raphaëlle, replay resolution and manifest access. Search, season navigation and visible Astrid et Raphaëlle replay playback were also verified inside CloudStream on a Pixel 7a emulator. That replay was unprotected DASH, so this does not validate Widevine license acquisition.

Protected playback, physical-device behavior, Chromecast, account-only programmes, live TV and downloads are not validated or supported as noted above. The home page shows each shelf's initial selection. Unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon FranceTV:testDebugUnitTest FranceTV:make
```

Tests cover episode numbering, poster preference, catalogue filtering and exclusion of recommendation shelves. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [france.tv connector (`francetv.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/francetv.py) and [shared playback resolver](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/resolver_proxy.py). They provide the protocol reference for this Kotlin implementation.

### Additional references

- [france.tv](https://www.france.tv/)
- [License](../../LICENSE)
