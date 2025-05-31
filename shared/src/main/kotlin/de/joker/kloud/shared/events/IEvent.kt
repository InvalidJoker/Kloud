package de.joker.kloud.shared.events

import kotlinx.serialization.Serializable

@Serializable
sealed interface IEvent {
    val eventName: String
}