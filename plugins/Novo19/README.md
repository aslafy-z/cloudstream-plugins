# Novo19

Unofficial NOVO19 provider for documentaries, films, entertainment and replay series.

Requires Android 6 (API 23) or newer. Programme rights and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse programme categories with posters and descriptions.
- Search programme titles across the available category catalogue.
- Load series episodes in official season and episode order, excluding recommendations.
- Play DASH streams with Widevine through Android's CDM.
- Direct Chromecast playback through a local manifest and licence relay.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. Each replay resolution obtains a fresh anonymous Red Bee session. Session tokens are not persisted.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **NOVO19 direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network: the phone relays the manifest and licenses, while the TV downloads video segments directly. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive relay links. Some versions have an episode-index bug on later seasons. The [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) ships in the [CloudStream pre-release](https://github.com/recloudstream/cloudstream/releases/tag/pre-release). The plugin's direct action works independently of that host bug.

Unprotected replays are plain DASH and are sent to the TV directly, without the relay.

## Diagnostics

API failures report HTTP status codes. Missing catalogue data and unavailable streams produce explicit messages. Chromecast relay failures are shown as a message with the failing step. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

The client reads the website's embedded Next.js catalogue state. Season selection matches the corresponding catalogue section, excludes the recommendation rail and follows supplied continuation pages with origin and loop checks.

Replay authorization uses the official Red Bee anonymous-session and entitlement endpoints. Android's CDM obtains the Widevine license from the supplied endpoint. The plugin does not extract encryption keys or forward the anonymous bearer token to media or license servers. Manifest audio and subtitle tracks remain available to the player.

### Chromecast relay

CloudStream's standard Cast sends the TV only the media URL, so the TV cannot learn the Widevine license endpoint. A temporary HTTP server on the phone serves the manifest and forwards license requests. The TV downloads video segments directly from the CDN.

1. CloudStream connects to its standard Cast receiver.
2. The plugin resolves the replay and fetches its DASH manifest.
3. The manifest receives an absolute CDN `BaseURL` and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
4. Each CDM challenge is forwarded unchanged to the Red Bee `licenseServerUrl` without additional headers; the raw license response is returned to the TV.
5. Chromecast acquires the license and decrypts the content using its own Widevine CDM.

The relay binds to the network interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers and contain no tokens. Request bodies are limited to 64 KiB, headers and connections are bounded, at most four sessions are kept and sessions expire after six hours without requests. Ending the Cast session or unloading the plugin closes the server.

CloudStream shares its link cache between local playback and Cast. The plugin invalidates only its own entries when Cast sessions change or a relay session is evicted. While the standard receiver is connected, relay links also carry the DRM configuration needed by the local player.

**NOVO19 direct Chromecast (relay)** resolves a fresh link and sends `RemoteMediaClient.load` to the standard session with one DASH media item and the resume position. It does not automatically play the next episode.

## Status and limitations

Live checks verified categories, programme tiles, season metadata, anonymous authorization and DASH manifest access. Search, film details and visible Widevine-protected playback of **Basile Bourgnon : l'héritier du large** were verified inside CloudStream on a Pixel 7a emulator.

Search filters the category catalogue by title; it is not a separate server search and its first request may take longer. Physical-device behavior, live TV and downloads have not been validated. Chromecast playback has not been validated on a physical Chromecast or any receiver; the relay and license forwarding are covered by unit tests only. Red Bee entitlement is geo-restricted, so the protected manifest and license exchange were not probed from outside France for this change. Live TV, podcasts and downloads are not offered. Website structure and unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon Novo19:testDebugUnitTest Novo19:make
```

Tests cover official episode numbers, recommendation exclusion, catalogue origin restrictions, the Chromecast manifest rewrite, relay access rules, license forwarding and the Cast LOAD request. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [NOVO19 connector (`novo19.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/novo19.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [NOVO19](https://novo19.ouest-france.fr/)
- [License](../../LICENSE)
