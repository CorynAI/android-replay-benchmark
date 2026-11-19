pluginManagement {
    includeBuild("build-logic")
    plugins {
        id("com.android.application") version "9.1.1"
        id("com.android.test") version "9.1.1"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus.retrec.io/repository/android-public")
    }
    versionCatalogs {
        create("libs") { from(files("versions.toml")) }
    }
}
rootProject.name = "CorynAndroidBenchmark"
include(":app", ":noSdk", ":tests")
