package de.joker.kloud.master.redis

import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.RedisHandler
import de.joker.kloud.shared.events.CreateServerEvent
import de.joker.kloud.shared.events.IEvent
import org.koin.java.KoinJavaComponent.inject

class ServerHandler: RedisHandler {
    override val channel = "servers"

    override fun handleEvent(event: IEvent) {
        when (event) {
            is CreateServerEvent -> {
                val manager: ServerManager by inject(ServerManager::class.java)
                val template: TemplateManager by inject(TemplateManager::class.java)

                val serverTemplate = template.getTemplate(event.template)
                if (serverTemplate == null) {
                    println("Template ${event.template} not found.")
                    return
                }
                manager.createServer(serverTemplate)
            }
            else -> {}
        }
    }
}
