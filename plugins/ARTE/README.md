# ARTE

Unofficial ARTE provider for French documentaries, films, series and concerts.

Requires Android 5 (API 21) or newer and a recent CloudStream pre-release. Geographic restrictions and programme availability apply.

## Features

- Browse ARTE's editorial home shelves and search programmes with posters.
- Open individual replays and collections, including paginated episode lists.
- Preserve available season and episode numbers; unnumbered videos retain catalogue order.
- Play HLS master playlists with their audio and subtitle tracks.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

No account or plugin configuration is required.

## Chromecast

Use CloudStream's standard **Cast episode** or **Cast mirror** action. The plugin supplies the HLS master playlist directly, without a phone relay. Track selection depends on the receiver.

The emulator receiver used during development cannot authenticate with Google's Android sender SDK. Full phone-to-TV Cast playback still needs validation on a certified receiver.

## Diagnostics

API failures report HTTP status codes. An empty player configuration reports that no compatible stream is available, including a reminder to check programme availability and region.

For player errors, use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat**. This plugin does not yet provide its own diagnostic report.

## Implementation

Catalogue and search use ARTE's French EMAC API. Playback uses the separate player configuration API. The public catalogue bearer identifier is never sent to the player endpoint. Pagination is restricted to the catalogue origin.

Collections use their primary video list when present, otherwise their editorial subcollections. Related programmes and trailers are excluded. Repeated video IDs are removed. The provider keeps the HLS master intact so the player can select its audio, subtitle and video tracks.

## Status and limitations

Search, replay details and advancing Karambolage playback were verified in CloudStream on a Pixel 7a emulator. API checks cover home shelves, collections and playback configuration. Physical-device Chromecast playback and all language-track combinations remain unverified.

The home page shows each shelf's initial selection; search provides additional programmes. Live TV, downloads, account features and a dedicated diagnostic report are not implemented. Unofficial APIs may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon ARTE:testDebugUnitTest ARTE:make
```

Tests cover catalogue mapping, unavailable videos, episode numbers, collection pagination, recommendation exclusion, stream selection and catalogue-token isolation. They do not replace device testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [ARTE connector (`arte.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/wo/arte.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [ARTE](https://www.arte.tv/fr/)
- [CloudStream plugin template](https://github.com/recloudstream/TestPlugins)
- [License](../../LICENSE)
