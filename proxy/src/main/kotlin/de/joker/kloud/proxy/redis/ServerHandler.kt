package de.joker.kloud.proxy.redis

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import de.joker.kloud.proxy.config.ConfigManager
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.redis.RedisHandler
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.utils.logger
import dev.fruxz.stacked.text
import org.koin.java.KoinJavaComponent.inject
import java.net.InetSocketAddress

val SerializableServer.serverInfo: ServerInfo?
    get() = ServerInfo(
        this.serverName,
        InetSocketAddress(
            "host.docker.internal",
            this.connectionPort
        )

    )

class ServerHandler : RedisHandler {
    override val channel = "servers"

    override fun handleEvent(event: IEvent) {
        when (event) {
            is ServerUpdateStateEvent -> {
                if (event.server.template.type != ServerType.PROXIED_SERVER) return

                val proxyServer: ProxyServer by inject(ProxyServer::class.java)

                val msg = when (event.state) {
                    ServerState.STARTING -> {
                        if (!ConfigManager.config.startingMessageEnabled || ConfigManager.config.startingMessage.isBlank()) return

                        text(
                            ConfigManager.config.startingMessage
                                .replace("{serverName}", event.server.serverName)
                            )

                    }
                    ServerState.RUNNING -> {
                        proxyServer.registerServer(event.server.serverInfo)

                        if (!ConfigManager.config.onlineNotificationEnabled || ConfigManager.config.onlineMessage.isBlank()) return

                        text(
                            ConfigManager.config.onlineMessage
                                .replace("{serverName}", event.server.serverName)
                        )
                    }

                    ServerState.GONE -> {
                        val serverInfo = proxyServer.getServer(event.server.serverName)
                        if (serverInfo != null && serverInfo.isPresent) {
                            proxyServer.unregisterServer(serverInfo.get().serverInfo)
                        } else {
                            logger.warn("Server ${event.server.serverName} not found in proxy.")
                        }

                        if (!ConfigManager.config.stopNotificationEnabled || ConfigManager.config.stopMessage.isBlank()) return

                        text(ConfigManager.config.stopMessage
                                .replace("{serverName}", event.server.serverName)
                            )
                    }
                }
                proxyServer.allPlayers.filter { player ->
                    player.hasPermission("kloud.notify")
                }.forEach { player ->
                    player.sendMessage(msg)
                }
            }

            else -> {}
        }
    }
}
