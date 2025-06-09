import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("xyz.jpenilla.run-velocity") version "2.3.1"
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(libs.bundles.utilities)
    shadow(libs.bundles.utilities)

    implementation(project(":shared"))
    shadow(project(":shared"))


    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)

    implementation(libs.bundles.redis)
    shadow(libs.bundles.redis)
    implementation(libs.bundles.koin)
    shadow(libs.bundles.koin)
    implementation(libs.stacked)
    shadow(libs.stacked)
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

tasks {
    build {
        dependsOn("shadowJar")
        dependsOn("processResources")
        finalizedBy("copyPlugin")
    }

    named("compileKotlin") {
        dependsOn("generateBuildConfig")
    }

    withType<ShadowJar> {
        mergeServiceFiles()
        configurations = listOf(project.configurations.shadow.get())
        archiveFileName.set("${project.name}.jar")
    }
}

val pluginOutputDir = "${project.rootDir}/cloud/templates/proxy/plugins"
val pluginJarName = "${project.name}.jar"

tasks.register<Copy>("copyPlugin") {
    from(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    into(pluginOutputDir)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("name", project.name)
    filesMatching("velocity-plugin.json") {
        expand(
            "version" to inputs.properties["version"],
            "name" to inputs.properties["name"]
        )
    }
}