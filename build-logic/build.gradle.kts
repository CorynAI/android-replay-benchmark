plugins { `kotlin-dsl` }

repositories { google(); mavenCentral(); gradlePluginPortal() }

dependencies {
    compileOnly("com.android.tools.build:gradle-api:9.1.1")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        register("benchmarkTransport") {
            id = "ai.coryn.benchmark-transport"
            implementationClass = "ai.coryn.benchmark.gradle.BenchmarkTransportPlugin"
        }
    }
}
