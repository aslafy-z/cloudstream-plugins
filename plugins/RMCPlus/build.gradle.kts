version = 1

cloudstream {
    description = "RMC+ documentaries, films and replay programmes"
    authors = listOf("aslafy-z")
    status = 3
    tvTypes = listOf("TvSeries", "Movie")
    language = "fr"
    requiresResources = false
}

android { namespace = "fr.rmcplus" }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

android { defaultConfig { minSdk = 23 } }
