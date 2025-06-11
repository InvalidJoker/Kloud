package de.joker.kloud.proxy.redis

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.redis.RedisHandler
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.utils.logger
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
                val redis: RedisSubscriber by inject(RedisSubscriber::class.java)


                if (event.server.template.type != ServerType.PROXIED_SERVER) return

                val proxyServer: ProxyServer by inject(ProxyServer::class.java)

                when (event.state) {
                    ServerState.RUNNING -> {
                        proxyServer.registerServer(event.server.serverInfo)
                    }

                    ServerState.GONE -> {
                        val serverInfo = proxyServer.getServer(event.server.serverName)
                        if (serverInfo != null && serverInfo.isPresent) {
                            proxyServer.unregisterServer(serverInfo.get().serverInfo)
                        } else {
                            logger.warn("Server ${event.server.serverName} not found in proxy.")
                        }
                    }

                    else -> {}
                }
            }

            else -> {}
        }
    }
}
