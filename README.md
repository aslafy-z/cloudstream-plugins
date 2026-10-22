# cloudstream-plugins

[CloudStream](https://github.com/recloudstream/cloudstream) plugins for French replay TV.

## Installation

Add one repository URL in CloudStream's extension settings.

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

## Plugins

| Plugin | Description |
| --- | --- |
| [M6Plus](plugins/M6Plus/README.md) | M6, W9, 6ter and Gulli replays with search, subtitles, Widevine and Chromecast |
| [TF1Plus](plugins/TF1Plus/README.md) | TF1+, TMC, TFX, TF1 Series Films and LCI replays with search, account settings, Widevine and Chromecast |
| [ARTE](plugins/ARTE/README.md) | ARTE documentaries, films, series and concerts with search and multilingual HLS playback |
| [FranceTV](plugins/FranceTV/README.md) | france.tv channel shelves, search, seasons and direct DASH/HLS replay playback |
| [RMCPlus](plugins/RMCPlus/README.md) | RMC+ channel shelves, search, encrypted account settings and Widevine replay playback |

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
