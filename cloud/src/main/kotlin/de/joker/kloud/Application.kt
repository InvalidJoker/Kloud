package de.joker.kloud

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json


@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    explicitNulls = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun main(args: Array<String>) {
    val b = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

    val docker = b.run {
        DockerClientBuilder.getInstance(this).build()
    }
}