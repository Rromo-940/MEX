import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Hentai"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://en.thehentai.net"
        lang = "en"
    }

    deeplink {
        path("/..*")
    }
}

android {
    sourceSets {
        named("test") {
            java.directories.add("test")
            kotlin.directories.add("test")
        }
    }
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.bundles.common)
}
tasks.matching { it.name.startsWith("ksp") && it.name.contains("UnitTest") }.configureEach { enabled = false }
