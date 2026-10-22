# csplugins

Dépôt multi-plugins CloudStream maintenu par **aslafy-z**. Chaque dossier contenant un `build.gradle.kts` est un plugin indépendant. Le premier module est **M6Plus**.

## Installation

Dans CloudStream, ajouter ce dépôt d'extensions :

```text
https://raw.githubusercontent.com/aslafy-z/csplugins/main/repo.json
```

Les [releases](https://github.com/aslafy-z/csplugins/releases) contiennent les fichiers **`.cs3`**, le catalogue `plugins.json`, `repo.json` et les sommes SHA-256. Les fichiers `.kt` sont les sources Kotlin ; le format installable CloudStream est `.cs3`.

## M6+ — expérimental

- Catalogue et catégories M6, W9, 6ter et Gulli.
- Recherche Algolia avec recherche catalogue de secours.
- Programmes, saisons, épisodes et clips de playlists exposés séparément.
- Connexion Gigya, échange JWT et renouvellement de session.
- Identifiants et session chiffrés avec une clé AES-GCM de l'Android Keystore.
- Lecture DASH avec la licence Widevine officielle du terminal et sous-titres VTT.
- Cache mémoire des métadonnées (10 minutes) ; le cache des affiches est géré par CloudStream.

Android 6/API 23 minimum. Ouvrir les paramètres du plugin pour se connecter avec un compte M6+ autorisé. Les restrictions géographiques et les droits du compte continuent de s'appliquer. Le téléchargement et Chromecast ne sont pas proposés.

**La compilation et les tests automatisés ne prouvent pas la lecture de bout en bout.** La connexion avec un compte réel, l'ergonomie à la télécommande, la licence Widevine et la reprise après redémarrage doivent encore être validées sur Android TV. Aucun compte ni identifiant utilisateur n'est livré dans ce dépôt.

Le lecteur CloudStream traite `keyRequestParameters` comme des options du CDM. Le plugin utilise `getVideoInterceptor()` pour ajouter l'en-tête HTTP `x-dt-auth-token` uniquement au serveur de licence DRMtoday et convertir une éventuelle réponse JSON `license` en octets pour le CDM. Il ne déchiffre pas les contenus et n'extrait aucune clé Widevine.

## Développement

Avec Java 17 et le SDK Android 35 configurés :

```sh
./gradlew M6Plus:testDebugUnitTest M6Plus:make
./gradlew make makePluginsJson
```

Pour ajouter un plugin, créer `NomDuPlugin/build.gradle.kts` et ses sources sous `src/main/kotlin`. La découverte des modules et la publication des assets sont automatiques. Incrémenter la version entière du module lorsqu'il change.

Chaque push sur `main` compile et teste tous les modules, puis publie une release `build-N`. Le catalogue de la dernière release pointe vers les assets de cette release précise. Une erreur de compilation ou de test empêche la publication.

## Références

Implémentation Kotlin indépendante du protocole décrit par [Catch-up TV & More](https://github.com/Catch-up-TV-and-More/plugin.video.catchuptvandmore/blob/dev/resources/lib/channels/fr/6play.py), à partir du [template public CloudStream](https://github.com/recloudstream/TestPlugins). Les API non officielles peuvent évoluer ; elles sont isolées dans `M6Plus/src/main/kotlin/fr/m6plus/api`.

Voir la [documentation CloudStream](https://recloudstream.github.io/csdocs/devs/gettingstarted/) pour la configuration du chargeur d'extensions.
