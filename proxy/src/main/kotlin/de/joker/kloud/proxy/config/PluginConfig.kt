package de.joker.kloud.proxy.config

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val startNotificationEnabled: Boolean,
    val stopNotificationEnabled: Boolean,
    val startMessage: String,
    val stopMessage: String,
    val cloudCommandEnabled: Boolean,
)