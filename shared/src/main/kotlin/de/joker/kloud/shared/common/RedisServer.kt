package de.joker.kloud.shared.common

import kotlinx.serialization.Serializable

@Serializable
data class RedisServer(
    val containerId: String,
    val templateName: String,
    val serverName: String,
    var serverData: ServerData,
    val connectionPort: Int,
    val type: ServerType,
    val lobby: Boolean = false,
)