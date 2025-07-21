plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.bundles.utilities)
    implementation(libs.redis)
    implementation(libs.bundles.grpc)
}