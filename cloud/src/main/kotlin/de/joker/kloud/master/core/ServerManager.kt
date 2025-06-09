package de.joker.kloud.master.core

import de.joker.kloud.master.template.Template
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.shared.logger
import de.joker.kloud.master.redis.RedisManager
import de.joker.kloud.shared.RedisNames
import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.common.ServerData
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import dev.fruxz.ascend.json.globalJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ServerManager : KoinComponent {
    val serverQueue = mutableMapOf<Template, Int>()
    val serverIds = mutableMapOf<String, Int>()
    val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    lateinit var shutdownJob: Job

    fun startup() {
        cleanupCurrent()
        cleanup()
        shutdownChecker()

        val templateManager: TemplateManager by inject()

        val static = templateManager.listTemplates().filter { it.dynamic == null }

        static.forEach { template ->
            createServer(template)
        }
    }

    fun cleanupCurrent() {
        val docker: DockerManager by inject()
        val containers = docker.getContainers()
        val templateManager: TemplateManager by inject()

        containers.forEach { container ->
            if (container.node?.labels?.containsKey("kloud-template") == true) {
                val templateName = container.node.labels["kloud-template"] ?: return@forEach
                val template = templateManager.getTemplate(templateName) ?: return@forEach

                try {
                    if (template.dynamic == null) {
                        logger.info("Stopping and deleting static server: ${container.name}")
                        docker.stopContainerBlocking(container.id) {
                            docker.deleteContainerBlocking(container.id, false)
                        }
                    } else {
                        logger.info("Deleting dynamic server: ${container.name}")
                        docker.deleteContainerBlocking(container.id, true)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to cleanup server ${container.name} (${container.id}): ${e.message}")
                    // Emit an event that the server is gone, even if it failed to stop
                    val redis: RedisManager by inject()
                    val stoppedEvent = ServerUpdateStateEvent(
                        container.id,
                        ServerState.GONE,
                    )
                    redis.emitEvent(RedisNames.SERVERS, stoppedEvent)
                }
            }
        }
    }

    fun createServer(
        template: Template
    ) {
        serverQueue[template] = serverQueue.getOrDefault(template, 0) + 1
        println("Creating server for template: ${template.name}")
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
        ) { res, port ->
            if (template.dynamic != null) {
                val currentId = serverIds[template.name] ?: 0
                serverIds[template.name] = currentId + 1
            }
            serverQueue[template] = maxOf(0, serverQueue.getOrDefault(template, 0) - 1)

            val redisServer = RedisServer(
                containerId = res.id,
                templateName = template.name,
                serverName = containerName,
                serverData = ServerData(),
                connectionPort = port,
                template.type,
                template.lobby,
            )
            redis.addToHash("servers", res.id, globalJson.encodeToString<RedisServer>(redisServer))
        }
    }

    fun updateServer(
        containerId: String,
        updateData: ServerData
    ) {
        val redis: RedisManager by inject()
        val server = redis.getFromHash("servers", containerId)?.let {
            globalJson.decodeFromString<RedisServer>(it)
        } ?: throw IllegalStateException("Server with container ID $containerId not found in Redis")

        server.serverData = updateData

        redis.addToHash("servers", containerId, globalJson.encodeToString<RedisServer>(server))
    }

    fun cleanup(after: () -> Unit = {}) {
        val redis: RedisManager by inject()
        val docker: DockerManager by inject()
        val template: TemplateManager by inject()

        val servers = redis.getHash("servers").map {
            globalJson.decodeFromString<RedisServer>(it.value)
        }

        if (::shutdownJob.isInitialized && shutdownJob.isActive) {
            shutdownJob.cancel()
            logger.info("Shutdown job cancelled.")
        }

        val runningFiles = File("./running")

        if (runningFiles.exists()) {
            runningFiles.deleteRecursively()
            logger.info("Deleted running files directory.")
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

                try {
                    docker.stopContainerBlocking(server.containerId) {
                        docker.deleteContainerBlocking(server.containerId, false)

                        val stoppedEvent = ServerUpdateStateEvent(
                            server.containerId,
                            ServerState.GONE,
                        )

                        redis.emitEvent(RedisNames.SERVERS, stoppedEvent)

                        redis.removeFromHash("servers", server.containerId)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to stop and delete static server ${server.serverName} (${server.containerId}): ${e.message}")
                    // Emit an event that the server is gone, even if it failed to stop
                    val stoppedEvent = ServerUpdateStateEvent(
                        server.containerId,
                        ServerState.GONE,
                    )
                    redis.emitEvent(RedisNames.SERVERS, stoppedEvent)
                    redis.removeFromHash("servers", server.containerId)
                }
            } else {// dynamic server, just delete it
                try {
                    docker.deleteContainerBlocking(server.containerId, true)
                } catch (e: Exception) {
                    logger.error("Failed to delete dynamic server ${server.serverName} (${server.containerId}): ${e.message}")
                }
                val stoppedEvent = ServerUpdateStateEvent(
                    server.containerId,
                    ServerState.GONE,
                )

                redis.emitEvent(RedisNames.SERVERS, stoppedEvent)

                redis.removeFromHash("servers", server.containerId)
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
                val servers = redis.getHash("servers").map {
                    globalJson.decodeFromString<RedisServer>(it.value)
                }

                servers.forEach { server ->
                    val state = docker.getContainerState(server.containerId)

                    if (state == null || state.running == false) {
                        logger.warn("Server ${server.serverName} (${server.templateName}) is not running, removing from Redis.")
                        redis.removeFromHash("servers", server.containerId)


                        val template = templateManager.getTemplate(server.templateName)
                            ?: throw IllegalStateException("Template ${server.templateName} not found for server ${server.serverName}")

                        if (template.dynamic != null) {
                            docker.deleteContainerInBackground(server.containerId, true)
                        }

                        val stoppedEvent = ServerUpdateStateEvent(
                            server.containerId,
                            ServerState.GONE,
                        )

                        redis.emitEvent(RedisNames.SERVERS, stoppedEvent)
                    }
                }

                val templates = templateManager.listTemplates()
                templates.filter { it.dynamic != null }.forEach { template ->
                    val runningServers = servers.count { it.templateName == template.name && docker.getContainerState(it.containerId)?.running == true }
                    val queuedServers = serverQueue[template] ?: 0

                    val minServers = template.dynamic?.minServers ?: 0

                    if (runningServers + queuedServers < minServers) {
                        repeat(minServers - runningServers) {
                            createServer(template)
                        }
                    }
                }

                delay(60000)
            }
        }
    }
}