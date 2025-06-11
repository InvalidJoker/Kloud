plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.bundles.utilities)
    implementation(libs.bundles.redis)
    implementation(libs.bundles.grpc)
}