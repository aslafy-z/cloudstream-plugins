# RMCPlus

Unofficial RMC+ provider for RMC Story, RMC Découverte and RMC Life replays.

Requires Android 6 (API 23) or newer. Account permissions and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse channel shelves and programme collections, with search and posters.
- Load replay lists with official season and episode numbers, ordered chronologically.
- Sign in, renew expired sessions and sign out from plugin settings.
- Play DASH/HLS streams with Widevine support through Android's CDM.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

Open **Settings → Extensions**, select the RMCPlus settings button and enter your RMC+ account credentials. Select **Sign in**, then close the settings once confirmation appears.

Credentials and the session token are encrypted using Android Keystore. Sessions survive application restarts; expired sessions renew using the saved account. **Sign out** removes the saved account and clears cached playback links. Restoring application data on another device requires signing in again.

## Chromecast

Chromecast is not enabled. The DRMtoday license response needs a compatible receiver or relay before Cast can be supported.

## Diagnostics

Open **Plugin diagnostics** in the plugin settings. The report includes timestamped sign-in, catalogue, search, playback and license events. **Copy** preserves line breaks, and **Clear** resets the report.

Diagnostics remain in memory and omit credentials, tokens, URLs and response bodies. For player errors, use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat**.

## Implementation

Catalogue and replay requests use the RMC+ BFF. Sign-in discovers the public client identifier from the current website and exchanges credentials through the official authentication endpoint. Authenticated API calls are restricted to the RMC+ BFF origin.

The player receives the DASH/HLS manifest. A license interceptor sends playback authorization only to the DRMtoday license endpoint and decodes its JSON transport envelope. Android's Widevine CDM handles the license; the plugin does not extract encryption keys. Manifest audio and subtitle tracks remain available to the player.

## Status and limitations

Account sign-in, search, programme details and visible Widevine-protected replay playback were verified in CloudStream on a Pixel 7a emulator using **Zone 51 : la base secrète qui intrigue la planète**. Channel endpoints and multi-season replay metadata were checked against the live API.

Physical-device playback, Chromecast, live TV and downloads have not been validated. Live TV and downloads are not offered. Catalogue shelves are loaded in batches; individual shelves expose the items returned by their component endpoint. Unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon RMCPlus:testDebugUnitTest RMCPlus:make
```

Tests cover episode numbering, ordering and deduplication, recommendation exclusion, authenticated endpoint restrictions and DRMtoday license transport decoding. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [RMC+ connector (`rmcplus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/rmcplus.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [RMC+](https://www.rmcplus.fr/)
- [License](../../LICENSE)
