import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.test") }

android {
    namespace = "ai.coryn.replay.benchmark.tests"
    compileSdk = libs.versions.sdk.compile.current.get().toInt()
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 26
        targetSdk = libs.versions.sdk.compile.current.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("recording", "sdk")
    }
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
