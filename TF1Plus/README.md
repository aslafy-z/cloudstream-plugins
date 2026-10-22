# TF1Plus (CloudStream)

Unofficial TF1+ CloudStream provider, including TF1, TMC, TFX, TF1 Séries Films and LCI.

Features:
- Browse channel programmes and search programme and replay metadata.
- Load programme replays, including SmartTV editorial season lists when available.
- Account settings inside CloudStream: sign in, sign out, and account status.
- AES-256-GCM encrypted credential storage using Android Keystore.
- DASH Widevine playback using the device's normal CDM, with licence headers restricted to the licence endpoint.
- In-memory catalogue cache; no backend, no API keys stored outside the client.

## Sign in
Install the plugin and open CloudStream > Extensions > TF1+ > Settings.
Enter the credentials for an existing TF1+ account. Login happens over HTTPS.
The password is never shown again, and the session token is only cached in memory.
The plugin attempts to renew the session when needed.

## Status / limitations
This is an experimental implementation adapted from the **TF1+** routes of
[Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/tf1plus.py).
The provider is not affiliated with TF1. GraphQL persisted-query IDs, login flows and stream
licence headers are subject to upstream changes. Use a valid account, compatible Widevine
device and an authorised region. No DRM circumvention or content downloads.
Chromecast and live TV are not implemented in this initial version.
The CI unit tests only cover synthetic payloads: verify sign-in, browsing and
protected playback on a physical device before releasing.

## Development
Run from the repository root:

```sh
./gradlew --no-daemon TF1Plus:testDebugUnitTest TF1Plus:make
```
