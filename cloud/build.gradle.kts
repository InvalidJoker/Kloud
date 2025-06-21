import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
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

tasks {
    withType<JavaCompile> {
        options.isFork = true
        options.isIncremental = true
    }

    test {
        useJUnitPlatform()
    }

    named("shadowJar", ShadowJar::class) {
        mergeServiceFiles()

        archiveFileName.set("${project.name}.jar")
    }
}

application {
    mainClass.set("de.joker.kloud.master.ApplicationKt")
}