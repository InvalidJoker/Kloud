package de.joker.kloud.proxy

import BuildConstants
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.proxy.redis.serverInfo
import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.common.ServerType
import de.joker.kloud.shared.logger
import dev.fruxz.ascend.json.globalJson
import dev.fruxz.stacked.text
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger
import kotlin.jvm.optionals.getOrNull

@Plugin(
    id = "kcloud-proxy",
    name = "Kloud Proxy",
    version = BuildConstants.VERSION,
    authors = ["InvalidJoker"]
)
class ProxyPlugin @Inject constructor(
    val server: ProxyServer,
) {
    @Subscribe
    fun handleInitialize(ignored: ProxyInitializeEvent) {
        val thisModule = module {
            single { server }
        }

        val redisModule = module {
            single { RedisSubscriber() }
        }

        startKoin {
            slf4jLogger()
            modules(
                thisModule,
                redisModule
            )
        }

        val redisSubscriber: RedisSubscriber by inject(RedisSubscriber::class.java)

        redisSubscriber.connect()

        val servers = redisSubscriber.getHash("servers")

        servers.forEach { server ->
            val serverData = globalJson.decodeFromString<RedisServer>(server.value)

            if (serverData.type != ServerType.PROXIED_SERVER) return@forEach

            this.server.registerServer(serverData.serverInfo)
            logger.info("Registered server: ${serverData.serverName} at port ${serverData.connectionPort}")
        }

        logger.info("Kloud Proxy initialized with ${servers.size} servers registered.")
    }


    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        val redis by inject<RedisSubscriber>(RedisSubscriber::class.java)
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


    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        val redis: RedisSubscriber by inject(RedisSubscriber::class.java)

        val serverName = event.server.serverInfo.name
        val servers = redis.getHash("servers").map {
            globalJson.decodeFromString<RedisServer>(it.value)
        }

        val availableServers = servers.filter { it.serverName != serverName && it.lobby }
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