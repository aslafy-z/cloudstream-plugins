# FranceTV

Unofficial france.tv provider for France 2, France 3, France 4, France 5, franceinfo and France tv Slash replays.

Requires Android 6 (API 23) or newer. Programme availability, account requirements and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse channel shelves and search programmes and full replays with posters.
- Load programme seasons and paginated episode lists, preserving official episode numbers.
- Exclude live entries, extracts, unpublished videos and recommendation shelves from episode lists.
- Resolve authorized DASH and HLS streams, with a separate Widevine path for protected DASH.
- Direct Chromecast playback, with a local manifest and license relay for protected DASH.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

The plugin uses replay endpoints that do not require a saved account. Account sign-in is not implemented; content requiring it may be unavailable.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **france.tv direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network. For protected replays, the phone relays the manifest and licenses while the TV downloads video segments directly. Unprotected replays are sent to the TV as is. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive relay links for protected replays. Some versions have an episode-index bug on later seasons. The [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) ships in the [CloudStream pre-release](https://github.com/recloudstream/cloudstream/releases/tag/pre-release). The plugin's direct action works independently of that host bug.

Relay failures are shown as a toast and as the link loading error. The official france.tv Cast receiver is not implemented.

## Diagnostics

API failures report HTTP status codes. Missing streams report programme or regional availability errors. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Catalogue and search use the Yatta mobile API. Replay resolution uses `k7.ftven.fr`, then the supplied stream is authorized through `hdfauth.ftven.fr`. The service determines the region; the client does not override the country.

For protected DASH, the client requests a Widevine authorization token and attaches it to the player's license request parameters. The device's CDM performs license acquisition. The plugin does not extract encryption keys. Audio and subtitle tracks embedded in the manifest remain available to the player.

### Chromecast relay

CloudStream's standard Cast path sends the TV only a URL and content type. The france.tv license server needs an `nv-authorizations` token plus `Origin`, `Referer` and `User-Agent` headers, which the receiver cannot add. For protected DASH:

1. CloudStream connects to its standard Cast receiver.
2. The plugin resolves the replay and fetches its DASH manifest with the client `User-Agent`.
3. The manifest gets an absolute `BaseURL` to the tokenized CDN path and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
4. Each CDM challenge is posted to `api-drm.ftven.fr` with the token and headers. The service answers with the raw license, which is returned unchanged. Redirects are rejected.
5. Chromecast acquires the license with its own Widevine CDM and downloads segments directly from the CDN.

The relay binds to the network interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers and contain no tokens. Request bodies are limited to 64 KiB, headers and connections are bounded, at most four sessions are kept, and sessions expire after six hours without requests. Ending the Cast session or unloading the plugin closes the server.

CloudStream shares its link cache between local playback and Cast. The plugin invalidates only its own entries when Cast sessions change or a relay session is evicted. While the standard receiver is connected, relay links also carry the DRM configuration needed by the local player.

Unprotected replays are not relayed. The authorized CDN URL carries its token in the path, so the TV fetches the manifest and segments itself, without the `User-Agent` that local playback sends. **france.tv direct Chromecast (relay)** sends `RemoteMediaClient.load` with one DASH or HLS media item and the resume position. It does not automatically play the next episode.

## Status and limitations

Live API checks verified channel shelves, search, all six seasons of Astrid et Raphaëlle, replay resolution and manifest access. Search, season navigation and visible Astrid et Raphaëlle replay playback were also verified inside CloudStream on a Pixel 7a emulator. That replay was unprotected DASH, so this does not validate Widevine license acquisition.

Chromecast playback has not been validated on a physical Chromecast, for protected or unprotected replays. Probes from outside France were geo-blocked at the CDN (HTTP 403), so it is unconfirmed whether segments load without the client `User-Agent` and whether the CDN sends `Access-Control-Allow-Origin` on successful responses; its error responses do carry other CORS headers. The license endpoint answered a service-certificate request with raw `application/octet-stream` and CORS for `https://www.france.tv`. Encrypted HLS is not supported.

Protected playback, physical-device behavior, account-only programmes, live TV and downloads are not validated or supported as noted above. The home page shows each shelf's initial selection. Unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon FranceTV:testDebugUnitTest FranceTV:make
```

Tests use synthetic data and cover episode numbering, poster preference, catalogue filtering, exclusion of recommendation shelves, manifest rewriting, license forwarding, Cast requests and relay access restrictions. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [france.tv connector (`francetv.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/francetv.py) and [shared playback resolver](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/resolver_proxy.py). They provide the protocol reference for this Kotlin implementation.

### Additional references

- [france.tv](https://www.france.tv/)
- [Google Cast DRM license declarations](https://developers.google.com/cast/docs/media/streaming_protocols#drm)
- [License](../../LICENSE)
