package de.joker.kloud.proxy.redis

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import de.joker.kloud.shared.RedisHandler
import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.common.ServerType
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.logger
import dev.fruxz.ascend.json.globalJson
import org.koin.java.KoinJavaComponent.inject
import java.net.InetSocketAddress

val RedisServer.serverInfo: ServerInfo?
    get() = ServerInfo(
        this.serverName,
        InetSocketAddress("localhost", this.connectionPort)
    )

class ServerHandler: RedisHandler {
    override val channel = "servers"

    override fun handleEvent(event: IEvent) {
        when (event) {
            is ServerUpdateStateEvent -> {
                val redis: RedisSubscriber by inject(RedisSubscriber::class.java)
                val server = redis.getFromHash("servers", event.serverId)?.let {
                    globalJson.decodeFromString<RedisServer>(it)
                }

                if (server == null) {
                    logger.warn("Server with ID ${event.serverId} not found in Redis.")
                    return
                }

                if (server.type != ServerType.PROXIED_SERVER) return

                val proxyServer: ProxyServer by inject(ProxyServer::class.java)

                when (event.state) {
                    ServerState.RUNNING -> {
                        proxyServer.registerServer(server.serverInfo)
                    }
                    ServerState.STOPPING -> {
                        val serverInfo = proxyServer.getServer(server.serverName)
                        if (serverInfo != null && serverInfo.isPresent) {
                            proxyServer.unregisterServer(serverInfo.get().serverInfo)
                        } else {
                            logger.warn("Server ${server.serverName} not found in proxy.")
                        }
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
}
