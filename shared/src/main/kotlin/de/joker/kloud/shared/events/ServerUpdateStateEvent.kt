package de.joker.kloud.shared.events

import de.joker.kloud.shared.server.SerializableServer
import kotlinx.serialization.Serializable

@Serializable
data class ServerUpdateStateEvent(
    val server: SerializableServer,
    val state: ServerState,
    override val eventName: String = "serverUpdateStateEvent"
) : IEvent