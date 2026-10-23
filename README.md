# cloudstream-plugins

[CloudStream](https://github.com/recloudstream/cloudstream) plugins for French replay TV.

## Installation

In CloudStream, open **Settings → Extensions → Add repository** and enter a short code or the corresponding URL.

| Channel | Short code |
| --- | --- |
| Stable | `replayfr` |
| Dev | `replayfrdev` |

**Stable**

```text
https://github.com/aslafy-z/cloudstream-plugins/releases/download/catalog-stable/repo.json
```

**Dev**

```text
https://github.com/aslafy-z/cloudstream-plugins/releases/download/catalog-dev/repo.json
```

Stable contains manually approved builds. Dev receives successful builds automatically.
To change channels, remove the installed plugins and their repository, then install from the other channel.

## Compatibility

Requires Android 6 (API 23) or newer. The plugins target CloudStream 4.8.0 APIs. Its stable release includes the DRM links, player interceptors and plugin settings used here.

Runtime playback tests currently use `4.8.0-PRE-castfix.1`, which includes the Chromecast episode-index correction from [CloudStream PR #3275](https://github.com/recloudstream/cloudstream/pull/3275). A [validated test APK and corresponding source](https://github.com/aslafy-z/cloudstream/releases/tag/cloudstream-castfix-1) are available. The patch addresses the standard **Cast episode** and **Cast mirror** actions on later seasons; M6+ and TF1+ also provide direct Cast actions that work independently of this host bug. The stable 4.8.0 APK has not been tested with every plugin.

See each plugin's README for its Android requirement and verified playback paths.

## Plugins

| Plugin | Description |
| --- | --- |
| [M6Plus](plugins/M6Plus/README.md) | M6, W9, 6ter and Gulli replays with search, subtitles, Widevine and Chromecast |
| [TF1Plus](plugins/TF1Plus/README.md) | TF1+, TMC, TFX, TF1 Series Films and LCI replays with search, account settings, Widevine and Chromecast |
| [ARTE](plugins/ARTE/README.md) | ARTE documentaries, films, series and concerts with search and multilingual HLS playback |
| [FranceTV](plugins/FranceTV/README.md) | france.tv channel shelves, search, seasons and direct DASH/HLS replay playback |
| [RMCPlus](plugins/RMCPlus/README.md) | RMC+ channel shelves, search, encrypted account settings and Widevine replay playback |
| [TV5MONDEPlus](plugins/TV5MONDEPlus/README.md) | TV5MONDEplus films and series with search, anonymous access and Widevine playback |
| [Novo19](plugins/Novo19/README.md) | NOVO19 categories, catalogue search, series and anonymous Widevine playback |
| [T18](plugins/T18/README.md) | T18 programme categories, catalogue search, Dailymotion HLS replays and Chromecast |

See each plugin's README for account setup and troubleshooting.

## Releases

Plugins are released independently with tags such as `M6Plus-v2026.10.22.37`.
Each plugin release includes its `.cs3`, build metadata and checksums.
The fixed `catalog-dev` and `catalog-stable` releases each contain `repo.json` and `plugins.json`.

To promote tested builds, open **Actions → Promote plugins to Stable → Run workflow** on `main`:

| Scope | Inputs |
| --- | --- |
| `all-latest` | Leave plugin and release blank to promote all current Dev builds |
| `one-plugin` | Enter a module name to promote its latest Dev build |
| `one-plugin` | Enter a module name and release tag to promote a specific build |

Promotion verifies the selected files and updates Stable without rebuilding.
To roll back, revert the plugin source and publish a new build; CloudStream does not automatically downgrade.

## Disclaimer

This is an independent, unofficial project. It is not affiliated with or endorsed by the broadcasters, streaming services or CloudStream developers. Service names and trademarks belong to their respective owners.

This repository distributes plugin code and does not host video content. Streams are supplied by the respective services or their media hosts. Use the plugins only to access content you are authorized to view, subject to applicable law, service terms, account requirements and geographic restrictions. The software license does not grant rights to third-party content.

For concerns about a hosted video, contact the service providing it. Concerns about code or other material in this repository can be reported through the [repository issues](https://github.com/aslafy-z/cloudstream-plugins/issues).

The software is provided as is, subject to the warranty disclaimer and limitation of liability in the [MIT License](LICENSE).

## Credits

Most of the API research and replay integration work behind these plugins comes from [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore) and its contributors. Their connectors are the foundation for the catalogue, authentication and playback flows implemented here in Kotlin for CloudStream. Each plugin's README links to its corresponding upstream implementation.

## Development

Requires Java 17 and Android SDK 35.

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
./gradlew --no-daemon testDebugUnitTest make makePluginsJson
```

Modules are discovered under `plugins/<Module>/`. Build one plugin with `./gradlew M6Plus:make`.
The output is `plugins/M6Plus/build/M6Plus.cs3`.

CI tests all plugins and publishes those with changed source or shared build inputs.
The build workflow's run number supplies CloudStream's integer plugin version. Preserve the workflow counter when replacing or renaming that workflow.

[CloudStream development documentation](https://recloudstream.github.io/csdocs/devs/gettingstarted/) · [License](LICENSE)
