package de.joker.kloud.master.redis

import de.joker.kloud.shared.RedisHandler
import de.joker.kloud.shared.events.CreateServerEvent
import de.joker.kloud.shared.events.IEvent

class ServerHandler: RedisHandler {
    override val channel = "servers"

    override fun handleEvent(event: IEvent) {
        when (event) {
            is CreateServerEvent -> {
                println("Handling CreateServerEvent with template: ${event.template}")
            }
        }
    }
}
