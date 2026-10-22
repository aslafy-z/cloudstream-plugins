# T18

Unofficial T18 provider for documentaries, magazines, entertainment and replay programmes.

Requires Android 5 (API 21) or newer. Geographic restrictions and programme availability apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse programme categories with posters and descriptions.
- Search titles across the programme catalogue.
- Load paginated replay lists, deduplicated and ordered by the supplied broadcast date.
- Resolve Dailymotion HLS streams with the required player headers.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. The plugin uses public programme pages and Dailymotion playback metadata.

## Chromecast

Chromecast is not enabled in this version. Receiver playback and the required media headers have not been validated.

## Diagnostics

HTTP failures, unavailable replays and missing Dailymotion streams produce explicit errors. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Programme categories and cards come from the T18 website. Replay lists use the programme's pagination endpoint. Recommendation cards are excluded from episode lists. Numbering reflects each replay's position in the available broadcast-date list; the service does not supply official season and episode numbers.

Playback resolves the Dailymotion identifier embedded in the replay page, then requests public player metadata using the replay URL as its embedder. HLS requests carry the Dailymotion player origin, referrer and user agent. Subtitle files are exposed when the service supplies them.

## Status and limitations

Live checks verified categories, programme pages, replay pagination, Dailymotion metadata and manifest access. Search, programme details, a 21-replay list spanning two pages and visible **Pour tout dire** playback were verified inside CloudStream on a Pixel 7a emulator.

Search filters programme titles rather than individual replay titles. Its first request may take longer while category pages load. Physical-device behavior, Chromecast, subtitles, live TV and downloads have not been validated. Live TV and downloads are not offered. T18's page structure and Dailymotion availability may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon T18:testDebugUnitTest T18:make
```

Tests cover lazy-loaded posters, programme-card filtering, replay ordering and deduplication, Dailymotion identifier validation and catalogue origin restrictions. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [T18 connector (`t18.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/t18.py). It provides the protocol reference for this Kotlin implementation, adapted to the current programme pages.

### Additional references

- [T18](https://t18.fr/)
- [License](../../LICENSE)
