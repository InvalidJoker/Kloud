package de.joker.kloud.shared

import de.joker.kloud.shared.events.IEvent

interface RedisHandler {
    val channel: String
    fun handleEvent(event: IEvent)
}