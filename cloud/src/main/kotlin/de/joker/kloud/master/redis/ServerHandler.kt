package de.joker.kloud.master.redis

import de.joker.kloud.shared.RedisHandler
import de.joker.kloud.shared.events.CreateServerEvent
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerUpdateStateEvent

class ServerHandler: RedisHandler {
    override val channel = "servers"

    override fun handleEvent(event: IEvent) {
        when (event) {
            is CreateServerEvent -> {
                println("Handling CreateServerEvent with template: ${event.template}")
            }
            is ServerUpdateStateEvent -> {
                println("Handling ServerUpdateStateEvent for serverId: ${event.serverId} with state: ${event.state}")
            }
        }
    }
}
