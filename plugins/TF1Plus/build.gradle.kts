version = 2

cloudstream {
    description = "TF1+, TMC, TFX, TF1 Series Films and LCI replays with Widevine"
    authors = listOf("aslafy-z")
    status = 3
    tvTypes = listOf("TvSeries", "Movie")
    language = "fr"
    requiresResources = false
}

android {
    namespace = "fr.tf1plus"
    defaultConfig { minSdk = 23 }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
