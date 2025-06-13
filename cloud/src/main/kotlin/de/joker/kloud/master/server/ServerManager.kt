package de.joker.kloud.master.server

import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.InternalApi
import de.joker.kloud.shared.events.CloudStartedEvent
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.redis.RedisNames
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.server.ServerData
import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.templates.Template
import de.joker.kloud.shared.utils.logger
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

@OptIn(InternalApi::class)
class ServerManager : KoinComponent {
    val serverQueue = mutableMapOf<Template, Int>()
    val serverIds = mutableMapOf<String, Int>()
    val scope = CoroutineScope(Dispatchers.IO)
    val redis: RedisConnector by inject()
    val docker: DockerIntegration by inject()
    val templates: TemplateManager by inject()


    lateinit var shutdownJob: Job

    fun startup() {
        clearState()
        launchShutdownMonitor()
        launchStaticServers()

        redis.publishEvent(RedisNames.CLOUD, CloudStartedEvent())
    }


    private fun clearState() {
        serverQueue.clear()
        serverIds.clear()
        cleanupCurrent()
    }

    private fun launchStaticServers() {
        val static = templates.listTemplates().filter { it.dynamic == null }
        scope.launch {
            static.forEach { template -> createServer(template, ServerData()) }
        }
    }

    fun cleanupCurrent() {
        val containers = docker.getContainers()

        containers.forEach { container ->


            try {
                val isRunning = docker.getContainerState(container.id)?.running ?: false
                if (isRunning) {
                    logger.info("Stopping server: ${container.name} (${container.id})")
                    docker.stopContainerBlocking(container.id)
                }
            } catch (e: Exception) {
                logger.error("Failed to cleanup server ${container.name} (${container.id}): ${e.message}")
            }

            val server = redis.getServerByContainer(container.id) ?: return@forEach
            val stoppedEvent = ServerUpdateStateEvent(
                server,
                ServerState.GONE,
            )
            redis.publishEvent(RedisNames.SERVERS, stoppedEvent)
        }

        val runningFiles = File("./running")

        if (runningFiles.exists()) {
            runningFiles.deleteRecursively()
            logger.info("Deleted running files directory.")
        }
    }

    fun restartServer(
        id: String
    ): Boolean {
        val server = redis.getServerByInternal(id) ?: return false

        logger.info("Restarting server: ${server.serverName}")

        docker.stopContainerInBackground(server.containerId) {
            redis.removeServer(id)
            val stoppedEvent = ServerUpdateStateEvent(
                server,
                ServerState.GONE
            )
            redis.publishEvent(RedisNames.SERVERS, stoppedEvent)
            scope.launch {
                createServer(server.template, server.serverData)
            }
        }

        return true

    }

    fun updateServer(
        id: String,
        serverData: ServerData
    ): Boolean {
        val server = redis.getServerByInternal(id) ?: return false

        logger.info("Updating server: ${server.serverName}")

        // Update the server data in Redis
        server.serverData = serverData
        redis.saveServer(server)

        return true

    }

    fun stopServer(
        id: String
    ): Boolean {
        val server = redis.getServerByInternal(id) ?: return false

        logger.info("Stopping server: ${server.serverName}")

        docker.stopContainerInBackground(server.containerId) {
            redis.removeServer(id)
            val stoppedEvent = ServerUpdateStateEvent(
                server,
                ServerState.GONE
            )
            redis.publishEvent(RedisNames.SERVERS, stoppedEvent)
        }

        return true

    }

    fun createServer(
        template: Template,
        serverData: ServerData
    ): String {
        serverQueue[template] = serverQueue.getOrDefault(template, 0) + 1
        println("Creating server for template: ${template.name}")

        val id = UUID.randomUUID().toString()

        if (template.dynamic == null && redis.getServerByInternal(id) != null) {
            throw IllegalStateException("Server with ID $id already exists for static template ${template.name}.")
        }

        val containerName = if (template.dynamic != null) {
            val ids = serverIds[template.name] ?: 1
            "${template.name}-${ids}"
        } else {
            template.name
        }

        docker.createContainer(
            id,
            template,
            containerName,
            onCreated = { res, port ->
                try {
                    if (template.dynamic != null) {
                        val currentId = serverIds[template.name] ?: 0
                        serverIds[template.name] = currentId + 1
                    }
                    serverQueue[template] = maxOf(0, serverQueue.getOrDefault(template, 0) - 1)

                    val serializableServer = SerializableServer(
                        internalId = id,
                        containerId = res.id,
                        template = template,
                        serverName = containerName,
                        serverData = serverData,
                        connectionPort = port
                    )
                    redis.saveServer(serializableServer)

                    val event = ServerUpdateStateEvent(
                        server = serializableServer,
                        state = ServerState.STARTING
                    )
                    redis.publishEvent(RedisNames.SERVERS, event)
                } catch (e: Exception) {
                    logger.error("Failed to create server $containerName: ${e.message}")
                }
            },
            onFinished = { res, port ->
                try {
                    val serializableServer = redis.getServerByContainer(res.id)
                        ?: run {
                            logger.error("Server with container ID ${res.id} not found in Redis.")
                            return@createContainer
                        }

                    val event = ServerUpdateStateEvent(
                        server = serializableServer,
                        state = ServerState.RUNNING
                    )
                    redis.publishEvent(RedisNames.SERVERS, event)
                } catch (e: Exception) {
                    logger.error("Failed to finish server creation for ${res.id}: ${e.message}")
                }
            }
        )

        return id
    }

    fun launchShutdownMonitor() {
        shutdownJob = scope.launch {
            while (true) {
                checkDeadServers()
                ensureMinDynamicServers()
                delay(10_000)
            }
        }
    }

    private fun checkDeadServers() {
        val servers = redis.getAllServers()

        servers.forEach { server ->
            val state = docker.getContainerState(server.containerId)
            val notRunning = state == null || (state.running == false && state.restarting == false)

            if (notRunning) {
                logger.warn("Removing non-running server: ${server.serverName}")
                redis.removeServer(server.internalId)

                redis.publishEvent(RedisNames.SERVERS, ServerUpdateStateEvent(server, ServerState.GONE))
            }
        }
    }

    private fun ensureMinDynamicServers() {
        val servers = redis.getAllServers()
        val sortedTemplates = templates.listTemplates().sortedByDescending { it.type == ServerType.PROXY }

        sortedTemplates.filter { it.dynamic != null }.forEach { template ->
            val running = servers.count {
                it.template.name == template.name && docker.getContainerState(it.containerId)?.running == true
            }
            val queued = serverQueue[template] ?: 0
            val min = template.dynamic?.minServers ?: 0

            if (running + queued < min) {
                repeat(min - running - queued) {
                    createServer(template, ServerData())
                }
            }
        }
    }
}