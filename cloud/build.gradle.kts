plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(libs.bundles.backend)
    implementation(libs.dockerJava)
}