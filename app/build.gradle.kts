import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.serialization)
    id("ai.coryn.benchmark-transport")
}

android {
    namespace = "ai.coryn.replay.benchmark"
    compileSdk = libs.versions.sdk.compile.current.get().toInt()
    defaultConfig {
        applicationId = "ai.coryn.replay.benchmark"
        minSdk = 26
        targetSdk = libs.versions.sdk.compile.current.get().toInt()
        versionCode = 1
        versionName = libs.versions.library.get()
        buildConfigField("String", "CORYN_VERSION", "\"${libs.versions.library.get()}\"")
    }
    flavorDimensions += "recording"
    productFlavors {
        create("sdk") {
            dimension = "recording"
            buildConfigField("boolean", "HAS_CORYN", "true")
        }
        create("viewer") {
            dimension = "recording"
            applicationIdSuffix = ".viewer"
            buildConfigField("boolean", "HAS_CORYN", "true")
        }
    }
    sourceSets.getByName("viewer").kotlin.srcDir("src/sdk/java")
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures.buildConfig = true
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)

dependencies {
    implementation(libs.serialization.kotlinx)
    "sdkImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
    "viewerImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    "sdkImplementation"("ai.coryn.replay.api:recorder:${libs.versions.library.get()}")
    "viewerImplementation"("ai.coryn.replay.api:recorder:${libs.versions.library.get()}")
    "viewerImplementation"("ai.coryn.replay.api:log-viewer:${libs.versions.library.get()}")
}
