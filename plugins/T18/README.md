# T18

Unofficial T18 provider for documentaries, magazines, entertainment and replay programmes.

Requires Android 6 (API 23) or newer. Geographic restrictions and programme availability apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse programme categories with posters and descriptions.
- Search titles across the programme catalogue.
- Load paginated replay lists, deduplicated and ordered by the supplied broadcast date, with source order reversed for episodes sharing a date.
- Resolve Dailymotion HLS streams with the required player headers.
- Direct Chromecast playback to the standard receiver, without a relay.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account is required. The plugin uses public programme pages and Dailymotion playback metadata.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **T18 direct Chromecast** | Connect using CloudStream's Cast button first. The TV downloads the playlists and video segments directly from Dailymotion; the phone only sends the LOAD request. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive the Dailymotion stream, but they do not declare the HLS segment format. Prefer the direct action if the TV refuses the standard one.

The Dailymotion Cast receiver is not implemented.

## Diagnostics

HTTP failures, unavailable replays and missing Dailymotion streams produce explicit errors. Use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat** for player errors. A dedicated plugin diagnostic report is not implemented.

## Implementation

Programme categories and cards come from the T18 website. Replay lists use the programme's pagination endpoint. Recommendation cards are excluded from episode lists. Numbering reflects each replay's position in the available broadcast-date list; the service does not supply official season and episode numbers.

Playback resolves the Dailymotion identifier embedded in the replay page, then requests public player metadata using the replay URL as its embedder. HLS requests carry the Dailymotion player origin, referrer and user agent. Subtitle files are exposed when the service supplies them.

### Chromecast playback

No relay is needed. Live checks on a public, non-geo-restricted Dailymotion video showed that the master playlist, media playlists, segments and subtitles load without the player headers, and that Dailymotion's CDN returns CORS headers for the standard receiver's origin (`https://www.gstatic.com`). The TV therefore receives the Dailymotion HLS address directly. T18 replays themselves were geo-blocked from the test location and were not probed.

**T18 direct Chromecast** resolves a fresh stream and sends `RemoteMediaClient.load` to the standard session. The request contains one HLS media item (`application/x-mpegURL`) and the resume position. The phone reads the master playlist and its first variant: when the variant declares an init segment (`#EXT-X-MAP`), the request marks the audio and video segments as fragmented MP4, which receivers otherwise assume to be MPEG-TS. Playlist reads are limited to 1 MiB. This action does not automatically play the next episode.

Sidecar subtitle files are SRT, which the standard receiver cannot render, so they are not sent to the TV. The HLS master also lists WebVTT subtitle renditions that the receiver can load itself. Stream addresses carry short-lived Dailymotion signatures and may be tied to the phone's public IP address; the TV normally shares it.

## Status and limitations

Live checks verified categories, programme pages, replay pagination, Dailymotion metadata and manifest access. Search, programme details, a 21-replay list spanning two pages and visible **Pour tout dire** playback were verified inside CloudStream on a Pixel 7a emulator.

Search filters programme titles rather than individual replay titles. Its first request may take longer while category pages load. Physical-device behavior, subtitles, live TV and downloads have not been validated. Chromecast playback on a physical receiver has not been validated, and neither have the receiver's HLS subtitle renditions. Live TV and downloads are not offered. T18's page structure and Dailymotion availability may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon T18:testDebugUnitTest T18:make
```

Tests cover lazy-loaded posters, programme-card filtering, replay ordering and deduplication, Dailymotion identifier validation, catalogue origin restrictions, HLS variant and segment-format detection, playlist size limits and the Cast LOAD request. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [T18 connector (`t18.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/t18.py). It provides the protocol reference for this Kotlin implementation, adapted to the current programme pages.

### Additional references

- [T18](https://t18.fr/)
- [License](../../LICENSE)
