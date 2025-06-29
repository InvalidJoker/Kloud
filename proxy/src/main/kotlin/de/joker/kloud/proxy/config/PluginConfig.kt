package de.joker.kloud.proxy.config

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val startingMessageEnabled: Boolean,
    val startingMessage: String,
    val onlineNotificationEnabled: Boolean,
    val onlineMessage: String,
    val stopNotificationEnabled: Boolean,
    val stopMessage: String,
    val cloudCommandEnabled: Boolean,
)