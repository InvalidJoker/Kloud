plugins {
    kotlin("jvm") version "2.1.21"
    alias(libs.plugins.kotlinPluginSerialization)
}

allprojects {
    group = "de.joker"
    repositories {
        mavenCentral()
        maven("https://nexus.fruxz.dev/repository/public/")
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    dependencies {
        implementation(rootProject.libs.bundles.kotlinxEcosystem)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}
