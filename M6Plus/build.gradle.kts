version = 1

cloudstream {
    description = "Replays M6+, W9, 6ter et Gulli avec lecture Widevine"
    authors = listOf("aslafy-z")
    status = 3
    tvTypes = listOf("TvSeries")
    language = "fr"
    requiresResources = false
}

android {
    namespace = "fr.m6plus"
    defaultConfig { minSdk = 23 }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
