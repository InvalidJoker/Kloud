package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
data class CreateServerEvent(
    val template: String,
    override val eventName: String = "createServerEvent"
) : IEvent