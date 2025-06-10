package de.joker.kloud.shared.server

import de.joker.kloud.shared.templates.Template
import kotlinx.serialization.Serializable

@Serializable
data class SerializableServer(
    val id: String,
    val serverName: String,
    val template: Template,
    var serverData: ServerData,
    val connectionPort: Int,
    val type: ServerType,
    val lobby: Boolean = false,
)