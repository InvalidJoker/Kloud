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
    implementation(libs.bundles.grpc)
    implementation(project(":shared"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("de.joker.kloud.master.ApplicationKt")
}