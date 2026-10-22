version = 3

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
    // Provided by CloudStream; never bundled into the plugin.
    compileOnly("com.google.android.gms:play-services-cast-framework:22.1.0")
    testImplementation("com.google.android.gms:play-services-cast-framework:22.1.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Robolectric downloads its Android runtime from Maven Central during tests only.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
