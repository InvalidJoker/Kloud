package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
enum class ServerState {
    STARTING,
    RUNNING,
    STOPPING,
    GONE,
}