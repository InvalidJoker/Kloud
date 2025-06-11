package de.joker.kloud.proxy.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.shared.utils.logger
import dev.fruxz.stacked.text
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.jvm.optionals.getOrNull

class KickListener: KoinComponent {

    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        val server: ProxyServer by inject()
        val redis: RedisSubscriber by inject()

        val serverName = event.server.serverInfo.name
        val servers = redis.getLobbyServers()

        val availableServers = servers.filter { it.serverName != serverName }
            .mapNotNull { server.getServer(it.serverName).getOrNull() }

        if (availableServers.isNotEmpty()) {
            val newServer = availableServers.minByOrNull { it.playersConnected.size } ?: availableServers.first()
            event.result = KickedFromServerEvent.RedirectPlayer.create(newServer)
            logger.info("Player ${event.player.username} reassigned to server: ${newServer.serverInfo.name}")
        } else {
            logger.warn("No available servers to reassign player ${event.player.username} after being kicked from ${serverName}.")
            event.result = KickedFromServerEvent.Notify.create(
                text("Sorry, no available servers at the moment. Please try again later.")
            )
        }
    }
}