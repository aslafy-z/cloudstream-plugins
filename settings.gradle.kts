rootProject.name = "CloudstreamPlugins"

// Keep Gradle project names stable while sources live under plugins/.
file("plugins").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
    ?.sortedBy { it.name }?.forEach { directory ->
        include(":${directory.name}")
        project(":${directory.name}").projectDir = directory
    }
