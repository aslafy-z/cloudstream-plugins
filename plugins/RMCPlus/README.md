# RMCPlus

Unofficial RMC+ provider for RMC Story, RMC Découverte and RMC Life replays.

Requires Android 6 (API 23) or newer. Account permissions and geographic restrictions apply.

Targets CloudStream 4.8.0 APIs. See [compatibility and tested builds](../../README.md#compatibility).

## Features

- Browse channel shelves and programme collections, with search and posters.
- Load replay lists with official season and episode numbers, ordered chronologically.
- Sign in, renew expired sessions and sign out from plugin settings.
- Play DASH/HLS streams with Widevine support through Android's CDM.
- Cast Widevine DASH replays to the standard Chromecast receiver through a local relay.
- Cache catalogue responses in memory for 10 minutes; CloudStream manages image caching.

## Sign in

Open **Settings → Extensions**, select the RMCPlus settings button and enter your RMC+ account credentials. Select **Sign in**, then close the settings once confirmation appears.

Credentials and the session token are encrypted using Android Keystore. Sessions survive application restarts; expired sessions renew using the saved account. **Sign out** removes the saved account and clears cached playback links. Restoring application data on another device requires signing in again.

## Chromecast

The episode menu offers one action:

| Action | Usage |
| --- | --- |
| **RMC+ direct Chromecast (relay)** | Connect using CloudStream's Cast button first. Keep the app open and the phone on the TV's network: the phone relays the manifest and licenses, while the TV downloads video segments directly. |

CloudStream's standard **Cast episode** and **Cast mirror** actions also receive relay links. Some versions have an episode-index bug on later seasons. The [upstream fix](https://github.com/recloudstream/cloudstream/pull/3275) ships in the [CloudStream pre-release](https://github.com/recloudstream/cloudstream/releases/tag/pre-release). The plugin's direct action works independently of that host bug.

Unprotected replays are sent to the TV unchanged. Widevine HLS replays cannot be cast: the plugin shows an error and asks to disconnect Chromecast to play them on the phone. The official RMC+ Cast receiver is not implemented.

## Diagnostics

Open **Plugin diagnostics** in the plugin settings. The report includes timestamped sign-in, catalogue, search, playback, license and Chromecast events. A Cast failure message names the last stage reached, such as `FETCH` or `REWRITE`. **Copy** preserves line breaks, and **Clear** resets the report.

Diagnostics remain in memory and omit credentials, tokens, URLs and response bodies. For player errors, use CloudStream's Logcat export under **Settings → Updates and backup → Show logcat**.

## Implementation

Catalogue and replay requests use the RMC+ BFF. Sign-in discovers the public client identifier from the current website and exchanges credentials through the official authentication endpoint. Authenticated API calls are restricted to the RMC+ BFF origin.

The player receives the DASH/HLS manifest. A license interceptor sends playback authorization only to the DRMtoday license endpoint and decodes its JSON transport envelope. Android's Widevine CDM handles the license; the plugin does not extract encryption keys. Manifest audio and subtitle tracks remain available to the player.

### Chromecast relay

CloudStream's standard Cast sends the TV only a URL and content type, so the DRMtoday token and JSON license envelope cannot reach the receiver. While the standard receiver is connected, the plugin starts a temporary HTTP server on the phone:

1. The plugin resolves the replay and fetches its DASH manifest.
2. The manifest gets an absolute CDN `BaseURL` and advertises a local license URL through `dashif:Laurl` and `ms:laurl`.
3. Each CDM challenge is posted to DRMtoday with the `x-dt-auth-token` header; redirects are rejected. The JSON envelope is decoded and the TV receives raw license bytes.
4. Chromecast downloads segments directly from the CDN and decrypts them with its own Widevine CDM.

The relay binds to the interface used to reach the TV and accepts only the connected TV and the phone itself. Session paths use random identifiers and contain no tokens. Request bodies are limited to 64 KiB, headers and connections are bounded, at most four sessions are kept and sessions expire after six hours without requests. Ending the Cast session, signing in or out, or unloading the plugin closes the server and clears only this plugin's cached links.

**RMC+ direct Chromecast (relay)** resolves a fresh link and sends one bounded `RemoteMediaClient.load` request with the title and resume position. It does not automatically play the next episode.

## Status and limitations

Account sign-in, search, programme details and visible Widevine-protected replay playback were verified in CloudStream on a Pixel 7a emulator using **Zone 51 : la base secrète qui intrigue la planète**. Channel endpoints and multi-season replay metadata were checked against the live API.

Physical-device playback, live TV and downloads have not been validated. Chromecast playback on a physical receiver has not been validated; relay, manifest rewrite and license forwarding are covered by unit tests only. The RMC+ CDN must allow the receiver's cross-origin segment requests; this was not verified because stream URLs require a signed-in account. Live TV and downloads are not offered. Catalogue shelves are loaded in batches; individual shelves expose the items returned by their component endpoint. Unofficial endpoints may change.

## Development

From the repository root:

```sh
./gradlew --no-daemon RMCPlus:testDebugUnitTest RMCPlus:make
```

Tests cover episode numbering, ordering and deduplication, recommendation exclusion, authenticated endpoint restrictions, DRMtoday license transport decoding, the Cast relay, manifest rewrite, license forwarding and the Cast LOAD request, using synthetic data. They do not replace device playback testing.

## Sources and credits

Most of the API research and replay integration work comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors, particularly the [RMC+ connector (`rmcplus.py`)](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/rmcplus.py). It provides the protocol reference for this Kotlin implementation.

### Additional references

- [RMC+](https://www.rmcplus.fr/)
- [License](../../LICENSE)
