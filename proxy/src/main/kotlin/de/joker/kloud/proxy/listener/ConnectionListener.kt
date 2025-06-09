package de.joker.kloud.proxy.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.logger
import dev.fruxz.ascend.json.globalJson
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConnectionListener: KoinComponent {

    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        val server: ProxyServer by inject()
        val redis: RedisSubscriber by inject()
        val redisServer = redis.getHash("servers").map {
            globalJson.decodeFromString<RedisServer>(it.value)
        }

        val servers = server.allServers
            .filter { redisServer.find { server -> server.serverName == it.serverInfo.name }?.lobby == true }

        if (servers.isEmpty()) {
            logger.warn("No lobby servers available for player ${event.player.username}.")

            return
        }

        // get lobby with least players
        val lobbyServer = servers.minByOrNull { it.playersConnected.size } ?: servers.firstOrNull()

        if (lobbyServer != null) {
            logger.info("Player ${event.player.username}: ${lobbyServer.serverInfo.name}, port ${lobbyServer.serverInfo.address.port}")
            event.setInitialServer(lobbyServer)
        } else {
            logger.warn("No lobby server found for player ${event.player.username}.")
        }
    }

}