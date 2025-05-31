plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(libs.bundles.backend)
    implementation(libs.bundles.utilities)
    implementation(project(":shared"))
}

application {
    mainClass.set("de.joker.kloud.ApplicationKt")
}