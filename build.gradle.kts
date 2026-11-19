plugins {
    id("com.android.application") apply false
    id("com.android.test") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

if (project == rootProject) {
    subprojects { layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("build/$name")) }
}
