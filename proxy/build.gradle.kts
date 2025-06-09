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

abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val buildConfigFile = outputDir.get().file("BuildConstants.kt").asFile
        buildConfigFile.parentFile.mkdirs()
        buildConfigFile.writeText("""
            object BuildConstants {
                const val VERSION = "${version.get()}"
            }
        """.trimIndent())
    }
}

tasks.register<GenerateBuildConfigTask>("generateBuildConfig") {
    version.set(rootProject.version.toString())
    outputDir.set(layout.buildDirectory.dir("generated/source/buildConfig"))
}

sourceSets.main {
    kotlin.srcDir("build/generated/source/buildConfig")
}

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}