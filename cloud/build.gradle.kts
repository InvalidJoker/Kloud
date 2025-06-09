plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(libs.bundles.backend)
    implementation(libs.bundles.utilities)
    implementation(libs.bundles.redis)
    implementation(libs.bundles.koin)
    implementation(libs.yaml)
    implementation(project(":shared"))
}

application {
    mainClass.set("de.joker.kloud.master.ApplicationKt")
}