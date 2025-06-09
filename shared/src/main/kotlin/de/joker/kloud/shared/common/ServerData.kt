package de.joker.kloud.shared.common

import kotlinx.serialization.Serializable

@Serializable
data class ServerData(
    val privateGame: PrivateGame? = null,
    val extraData: Map<String, String> = emptyMap(),
)