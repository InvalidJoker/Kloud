package de.joker.kloud.shared.templates

import kotlinx.serialization.Serializable

@Serializable
data class DynamicTemplate(
    val minServers: Int,
    val maxServers: Int,
    val extraDirectories: List<String> = emptyList(),
) {
    fun toProto(): build.buf.gen.templates.v1.DynamicTemplate {
        return build.buf.gen.templates.v1.DynamicTemplate.newBuilder()
            .setMinServers(minServers)
            .setMaxServers(maxServers)
            .addAllExtraDirectories(extraDirectories)
            .build()
    }
}