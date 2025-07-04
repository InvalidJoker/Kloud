package de.joker.kloud.proxy

import BuildConstants
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import de.joker.kloud.proxy.command.CloudCommand
import de.joker.kloud.proxy.config.ConfigManager
import de.joker.kloud.proxy.listener.ConnectionListener
import de.joker.kloud.proxy.listener.KickListener
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.proxy.redis.serverInfo
import de.joker.kloud.shared.api.APIWrapper
import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.utils.logger
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIVelocityConfig
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger
import java.nio.file.Path

@Plugin(
    id = "kloud-proxy",
    name = "Kloud Proxy",
    version = BuildConstants.VERSION,
    authors = ["InvalidJoker"]
)
class ProxyPlugin @Inject constructor(
    @DataDirectory val dataDirectory: Path,
    val server: ProxyServer,
) {
    companion object {
        lateinit var instance: ProxyPlugin
            private set
    }

    init {
        CommandAPI.onLoad(CommandAPIVelocityConfig(server, this).setNamespace("kloud"));
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Subscribe
    fun handleInitialize(ignored: ProxyInitializeEvent) {
        instance = this

        val token = System.getenv("KLOUD_API_TOKEN")
            ?: throw IllegalStateException("KLOUD_API_TOKEN environment variable is not set.")
        val port = System.getenv("KLOUD_API_PORT")?.toIntOrNull()
            ?: throw IllegalStateException("KLOUD_API_PORT environment variable is not set or invalid.")

        val thisModule = module {
            single { server }
            single { RedisSubscriber() }
            single { APIWrapper(
                host = "host.docker.internal",
                token = token,
                port = port
            ) }
        }

        startKoin {
            slf4jLogger()
            modules(
                thisModule
            )
        }

        ConfigManager.loadConfig(dataDirectory)

        CommandAPI.onEnable();

        if (ConfigManager.config.cloudCommandEnabled) {
            CloudCommand.register();
        }

        server.eventManager.register(this, ConnectionListener())
        server.eventManager.register(this, KickListener())

        val redisSubscriber: RedisSubscriber by inject(RedisSubscriber::class.java)

        redisSubscriber.connect()

        val servers = redisSubscriber.getAllServers()

        // remove all existing servers in the proxy
        server.allServers.forEach { existingServer ->
            val name = existingServer.serverInfo.name
            if (servers.none { it.serverName == name }) {
                server.unregisterServer(existingServer.serverInfo)
                logger.info("Unregistered server: $name")
            }
        }

        servers.forEach { server ->
            if (server.template.type != ServerType.PROXIED_SERVER) return@forEach

            this.server.registerServer(server.serverInfo)
            logger.info("Registered server: ${server.serverName} at port ${server.connectionPort}")
        }

        logger.info("Kloud Proxy initialized with ${servers.size} servers registered.")
    }
}