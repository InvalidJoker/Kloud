package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
data class ServerUpdateStateEvent(
    val serverId: String,
    val state: ServerState,
    override val eventName: String = "serverUpdateStateEvent"
) : IEvent