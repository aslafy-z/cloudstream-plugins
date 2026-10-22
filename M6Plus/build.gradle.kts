version = 1

cloudstream {
    description = "M6+, W9, 6ter and Gulli replays with Widevine playback"
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
    // Provided by CloudStream's Media3 Cast dependency; never bundled into the .cs3.
    compileOnly("com.google.android.gms:play-services-cast-framework:22.1.0")
    compileOnly("androidx.mediarouter:mediarouter:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.android.gms:play-services-cast-framework:22.1.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Robolectric downloads its Android runtime from Maven Central during tests only.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
