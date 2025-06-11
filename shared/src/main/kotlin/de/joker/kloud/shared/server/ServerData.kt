package de.joker.kloud.shared.server

import kotlinx.serialization.Serializable

@Serializable
data class ServerData(
    val privateGame: PrivateGame? = null,
    val extraData: Map<String, String> = emptyMap(),
)