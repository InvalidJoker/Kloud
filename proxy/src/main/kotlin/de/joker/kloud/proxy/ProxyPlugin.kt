package de.joker.kloud.proxy

import BuildConstants
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import de.joker.kloud.proxy.listener.ConnectionListener
import de.joker.kloud.proxy.listener.KickListener
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.proxy.redis.serverInfo
import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.common.ServerType
import de.joker.kloud.shared.logger
import dev.fruxz.ascend.json.globalJson
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger

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

        server.eventManager.register(this, ConnectionListener())
        server.eventManager.register(this, KickListener())

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
}