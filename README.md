# cloudstream-plugins

[CloudStream](https://github.com/recloudstream/cloudstream) plugins maintained by **aslafy-z**. Each module is built and distributed independently as a `.cs3` file.

## Installation

Add this repository in CloudStream's extension settings:

```text
https://raw.githubusercontent.com/aslafy-z/cloudstream-plugins/main/repo.json
```

[Releases](https://github.com/aslafy-z/cloudstream-plugins/releases) include the `.cs3` plugins, `plugins.json` catalogue, `repo.json`, and SHA-256 checksums.

## Plugins

| Plugin | Description |
| --- | --- |
| [M6Plus](M6Plus/README.md) | M6, W9, 6ter and Gulli replays with search, subtitles, Widevine and Chromecast |
| [TF1Plus](TF1Plus/README.md) | TF1+, TMC, TFX, TF1 Séries Films and LCI replays with search, account settings and Widevine |

Each plugin's README contains its requirements, setup instructions, features and troubleshooting information.

## Development

Requires Java 17 and Android SDK 35.

```sh
./gradlew --no-daemon testDebugUnitTest make makePluginsJson
python3 scripts/package_release.py --repository aslafy-z/cloudstream-plugins --tag "$RELEASE_TAG"
```

To add a plugin, create its directory with a `build.gradle.kts`, a `README.md` and sources under `src/main/kotlin`. Modules are discovered automatically.

CI tests and builds `main`, pull requests and release tags. To publish, push a new `vN` tag on the intended commit. CI publishes all plugins after tests and compilation succeed. Catalogue entries link to the assets of that specific tag. Published release tags should not be reused.

## References

Built from the [CloudStream plugin template](https://github.com/recloudstream/TestPlugins).

[CloudStream development documentation](https://recloudstream.github.io/csdocs/devs/gettingstarted/) · [License](LICENSE)
