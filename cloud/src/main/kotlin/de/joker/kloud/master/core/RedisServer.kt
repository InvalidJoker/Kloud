package de.joker.kloud.master.core

import kotlinx.serialization.Serializable

@Serializable
data class RedisServer(
    val containerId: String,
    val templateName: String,
    val serverName: String,
)