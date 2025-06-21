package de.joker.kloud.shared.templates

import kotlinx.serialization.Serializable
import build.buf.gen.templates.v1.BuildSettings as ProtoBuildSettings

@Serializable
data class BuildSettings(
    val image: String,
    val imageVersion: String = "latest",
) {
    fun toProto(): ProtoBuildSettings {
        return ProtoBuildSettings.newBuilder()
            .setImage(image)
            .setImageVersion(imageVersion)
            .build()
    }
}