package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
data class CloudStartedEvent(
    override val eventName: String = "cloudStartedEvent",
) : IEvent