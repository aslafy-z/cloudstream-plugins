version = 1

cloudstream {
    description = "T18 programmes and Dailymotion replays"
    authors = listOf("aslafy-z")
    status = 3
    tvTypes = listOf("TvSeries", "Movie")
    language = "fr"
    requiresResources = false
}

android { namespace = "fr.t18" }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

dependencies { implementation("org.jsoup:jsoup:1.18.3") }
