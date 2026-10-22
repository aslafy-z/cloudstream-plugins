# Novo19

Unofficial NOVO19 provider for documentaries, films, entertainment and replay series.

Requires Android 6 (API 23) or newer. Programme rights and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse programme categories with posters and descriptions.
- Search programme titles across the available category catalogue.
- Load series episodes in official season and episode order, excluding recommendations.
- Play DASH streams with Widevine through Android's CDM.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. Each replay resolution obtains a fresh anonymous Red Bee session. Session tokens are not persisted.

## Chromecast

Chromecast is not enabled in this version. Receiver playback and license handling have not been validated.

## Diagnostics

API failures report HTTP status codes. Missing catalogue data and unavailable streams produce explicit messages. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

The client reads the website's embedded Next.js catalogue state. Season selection matches the corresponding catalogue section, excludes the recommendation rail and follows supplied continuation pages with origin and loop checks.

Replay authorization uses the official Red Bee anonymous-session and entitlement endpoints. Android's CDM obtains the Widevine license from the supplied endpoint. The plugin does not extract encryption keys or forward the anonymous bearer token to media or license servers. Manifest audio and subtitle tracks remain available to the player.

## Status and limitations

Live checks verified categories, programme tiles, season metadata, anonymous authorization and DASH manifest access. Search, film details and visible Widevine-protected playback of **Basile Bourgnon : l'héritier du large** were verified inside CloudStream on a Pixel 7a emulator.

Search filters the category catalogue by title; it is not a separate server search and its first request may take longer. Physical-device behavior, Chromecast, live TV and downloads have not been validated. Live TV, podcasts and downloads are not offered. Website structure and unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon Novo19:testDebugUnitTest Novo19:make
```

Tests cover official episode numbers, recommendation exclusion and catalogue origin restrictions. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [NOVO19 connector (`novo19.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/novo19.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [NOVO19](https://novo19.ouest-france.fr/)
- [License](../../LICENSE)
