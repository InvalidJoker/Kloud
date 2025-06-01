package de.joker.kloud.master.core

import de.joker.kloud.master.data.Template
import de.joker.kloud.master.data.TemplateManager
import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.master.json
import de.joker.kloud.master.logger
import de.joker.kloud.master.redis.RedisManager
import de.joker.kloud.master.redis.RedisNames
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ServerManager : KoinComponent {
    val serverIds = mutableMapOf<String, Int>()
    val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    lateinit var shutdownJob: Job

    fun startup() {
        cleanup()
        val templateManager: TemplateManager by inject()

        val templates = templateManager.listTemplates()

        shutdownChecker()

        templates.forEach { template ->
            if (template.lobby) {
                createServer(template)
            }
        }
    }

    fun createServer(
        template: Template
    ) {
        val redis: RedisManager by inject()
        val docker: DockerManager by inject()

        val containerName = if (template.dynamic != null) {
            val ids = serverIds[template.name] ?: 1

            "${template.name}-${ids}"
        } else {
            template.name
        }

        docker.createContainer(
            template,
            containerName,
        ) {
            if (template.dynamic != null) {
                val currentId = serverIds[template.name] ?: 0
                serverIds[template.name] = currentId + 1
            }

            val redisServer = RedisServer(
                containerId = it.id,
                templateName = template.name,
                serverName = containerName,
            )
            redis.addToList("servers", json.encodeToString<RedisServer>(redisServer))
        }
    }

    fun cleanup(after: () -> Unit = {}) {
        val redis: RedisManager by inject()
        val docker: DockerManager by inject()
        val template: TemplateManager by inject()

        val servers = redis.getList("servers").map {
            json.decodeFromString<RedisServer>(it)
        }

        if (::shutdownJob.isInitialized && shutdownJob.isActive) {
            shutdownJob.cancel()
            logger.info("Shutdown job cancelled.")
        }

        servers.forEach { server ->
            val serverTemplate = template.getTemplate(server.templateName) ?: throw IllegalStateException("Template ${server.templateName} not found for server ${server.serverName}")

            if (serverTemplate.dynamic == null) {
                // static server, stop and delete it

                val stoppingEvent = ServerUpdateStateEvent(
                    server.containerId,
                    ServerState.STOPPING,
                )

                redis.emitEvent(RedisNames.SERVERS, stoppingEvent)

                docker.stopContainerBlocking(server.containerId) {
                    docker.deleteContainerBlocking(server.containerId, false)

                    val stoppedEvent = ServerUpdateStateEvent(
                        server.containerId,
                        ServerState.GONE,
                    )

                    redis.emitEvent(RedisNames.SERVERS, stoppedEvent)

                    redis.removeFromList("servers", json.encodeToString<RedisServer>(server))
                }
            } else {// dynamic server, just delete it
                docker.deleteContainerBlocking(server.containerId, true)
                redis.removeFromList("servers", json.encodeToString<RedisServer>(server))
            }
        }

        serverIds.clear()
        after.invoke()
    }

    fun shutdownChecker() {
        val docker: DockerManager by inject()
        val redis: RedisManager by inject()
        val templateManager: TemplateManager by inject()

        shutdownJob = scope.launch {
            while (true) {
                val servers = redis.getList("servers").map {
                    json.decodeFromString<RedisServer>(it)
                }

                servers.forEach { server ->
                    val state = docker.getContainerState(server.containerId)

                    if (state == null || state.running == false) {
                        logger.warn("Server ${server.serverName} (${server.templateName}) is not running, removing from Redis.")
                        redis.removeFromList("servers", json.encodeToString<RedisServer>(server))
                    }

                    val template = templateManager.getTemplate(server.templateName)
                        ?: throw IllegalStateException("Template ${server.templateName} not found for server ${server.serverName}")

                    if (template.dynamic != null) {
                        docker.deleteContainerInBackground(server.containerId, true)
                    }
                }

                delay(60000) // check every minute
            }
        }
    }
}