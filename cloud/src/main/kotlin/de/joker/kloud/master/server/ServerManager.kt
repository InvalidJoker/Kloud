package de.joker.kloud.master.server

import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.InternalApi
import de.joker.kloud.shared.events.CloudStartedEvent
import de.joker.kloud.shared.events.ServerState
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
    val scope = CoroutineScope(Dispatchers.IO)
    val redis: RedisConnector by inject()
    val docker: DockerIntegration by inject()
    val templates: TemplateManager by inject()


    lateinit var shutdownJob: Job

    fun startup() {
        clearState()
        launchShutdownMonitor()
        launchStaticServers()

        redis.emit(RedisNames.CLOUD, CloudStartedEvent())
    }


    private fun clearState() {
        serverQueue.clear()
        cleanupCurrent()
    }

    private fun launchStaticServers() {
        val static = templates.listTemplates().filter { it.dynamic == null }
        scope.launch {
            static.forEach { template -> createServer(template, ServerData()) }
        }
    }

    fun cleanupCurrent(stopAll: Boolean = false) {
        val containers = docker.getContainers()

        containers.forEach { container ->
            val redisServer = redis.getServerByContainer(container.id)

            val template = if (redisServer != null) {
                templates.getTemplate(redisServer.template.name)
            } else {
                null
            }

            if ((redisServer == null || redisServer.template != template) || stopAll) {
                try {
                    val isRunning = docker.getContainerState(container.id)?.running ?: false
                    if (isRunning) {
                        logger.info("Stopping server: ${container.name} (${container.id})")
                        docker.stopContainerBlocking(container.id)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to cleanup server ${container.name} (${container.id}): ${e.message}")
                }
            }
        }

        val runningFiles = File("./running")

        if (runningFiles.exists()) {
            runningFiles.listFiles()?.forEach { file ->
                try {
                    val id = file.nameWithoutExtension
                    if (redis.getServerByInternal(id) == null) {
                        logger.info("Removing stale running file: ${file.name}")
                        file.delete()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to remove stale running file ${file.name}: ${e.message}")
                }
            }
        }
    }

    fun restartServer(
        id: String
    ): Boolean {
        val server = redis.getServerByInternal(id) ?: return false

        logger.info("Restarting server: ${server.serverName}")

        docker.stopContainerInBackground(server.containerId) {
            redis.removeServer(id)
            redis.changeServerState(server, ServerState.GONE)
            docker.deleteServerDirectory(server)
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
            redis.changeServerState(server, ServerState.GONE)
            docker.deleteServerDirectory(server)
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

        // check if maximum servers reached
        if (template.dynamic != null && (serverQueue[template] ?: 0) > template.dynamic!!.maxServers) {
            logger.warn("Maximum servers reached for template ${template.name}.")
            throw IllegalStateException("Maximum servers reached for template ${template.name}.")
        }

        val containerName = if (template.dynamic != null) {
            val id = redis.getServersByTemplate(template.name).size + 1
            "${template.name}-${id}"
        } else {
            template.name
        }

        docker.createContainer(
            id,
            template,
            containerName,
            onCreated = { res, port ->
                try {
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
                    redis.changeServerState(serializableServer, ServerState.STARTING)
                } catch (e: Exception) {
                    logger.error("Failed to create server $containerName: ${e.message}")
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
                redis.changeServerState(server, ServerState.GONE)
                docker.deleteServerDirectory(server)
            }
        }
    }

    private fun ensureMinDynamicServers() {
        val servers = redis.getAllServers()
        val sortedTemplates = templates.listTemplates().sortedWith(
            compareByDescending<Template> { it.type == ServerType.PROXY }
                .thenByDescending { it.priority }
        )

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