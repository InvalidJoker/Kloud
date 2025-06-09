plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

dependencies {
    implementation(libs.bundles.utilities)
    implementation(project(":shared"))
    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)
    implementation(libs.bundles.redis)
    implementation(libs.bundles.koin)
    implementation(libs.stacked)
}

tasks {
    runVelocity {
        velocityVersion("3.4.0-SNAPSHOT")
    }
}


tasks.register("generateBuildConfig") {
    val outputDir = project.layout.buildDirectory.dir("generated/source/buildConfig").get().asFile
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val buildConfigFile = outputDir.resolve("BuildConstants.kt")
        buildConfigFile.writeText("""
            object BuildConstants {
                const val VERSION = "${rootProject.version}"
            }
        """.trimIndent())
    }
}

sourceSets.main {
    kotlin.srcDir("build/generated/source/buildConfig")
}

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}
