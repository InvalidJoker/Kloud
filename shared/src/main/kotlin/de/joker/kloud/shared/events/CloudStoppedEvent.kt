package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
data class CloudStoppedEvent(
    override val eventName: String = "cloudStoppedEvent"
) : IEvent