import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.coryn.replay.benchmark"
    compileSdk = libs.versions.sdk.compile.current.get().toInt()
    defaultConfig {
        applicationId = "ai.coryn.replay.benchmark.plain"
        minSdk = 26
        targetSdk = libs.versions.sdk.compile.current.get().toInt()
        versionCode = 1
        versionName = libs.versions.library.get()
        buildConfigField("String", "CORYN_VERSION", "\"${libs.versions.library.get()}\"")
        buildConfigField("boolean", "HAS_CORYN", "false")
    }
    sourceSets.getByName("main") {
        manifest.srcFile("../app/src/main/AndroidManifest.xml")
        kotlin.srcDirs("../app/src/main/java", "../app/src/plain/java")
        res.srcDir("../app/src/main/res")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../app/proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures.buildConfig = true
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)

dependencies { implementation(libs.serialization.kotlinx) }
