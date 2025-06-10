package de.joker.kloud.master.core

import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.redis.RedisNames
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.server.ServerData
import de.joker.kloud.shared.templates.Template
import de.joker.kloud.shared.utils.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ServerManager : KoinComponent {
    val serverQueue = mutableMapOf<Template, Int>()
    val serverIds = mutableMapOf<String, Int>()
    val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    lateinit var shutdownJob: Job

    fun startup() {
        serverQueue.clear()
        serverIds.clear()
        cleanupCurrent()
        shutdownChecker()


        val templateManager: TemplateManager by inject()

        val static = templateManager.listTemplates().filter { it.dynamic == null }

        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            static.forEach { template ->
                createServer(template, ServerData())
            }
        }
    }

    fun cleanupCurrent() {
        val docker: DockerIntegration by inject()
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
                    val redis: RedisConnector by inject()
                    val stoppedEvent = ServerUpdateStateEvent(
                        container.id,
                        ServerState.GONE,
                    )
                    redis.publishEvent(RedisNames.SERVERS, stoppedEvent)
                }
            }
        }

        val runningFiles = File("./running")

        if (runningFiles.exists()) {
            runningFiles.deleteRecursively()
            logger.info("Deleted running files directory.")
        }
    }


    suspend fun createServer(
        template: Template,
        serverData: ServerData
    ): String? = suspendCoroutine { cont ->
        serverQueue[template] = serverQueue.getOrDefault(template, 0) + 1
        println("Creating server for template: ${template.name}")
        val redis: RedisConnector by inject()
        val docker: DockerIntegration by inject()

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
            try {
                if (template.dynamic != null) {
                    val currentId = serverIds[template.name] ?: 0
                    serverIds[template.name] = currentId + 1
                }
                serverQueue[template] = maxOf(0, serverQueue.getOrDefault(template, 0) - 1)

                val serializableServer = SerializableServer(
                    id = res.id,
                    templateName = template.name,
                    serverName = containerName,
                    serverData = serverData,
                    connectionPort = port,
                    template.type,
                    template.lobby,
                )
                redis.saveServer(serializableServer)
                cont.resume(res.id)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }
    }

    fun shutdownChecker() {
        val docker: DockerIntegration by inject()
        val redis: RedisConnector by inject()
        val templateManager: TemplateManager by inject()

        shutdownJob = scope.launch {
            while (true) {
                val servers = redis.getAllServers()

                servers.forEach { server ->
                    val state = docker.getContainerState(server.id)

                    if (state == null || state.running == false) {
                        logger.warn("Server ${server.serverName} (${server.templateName}) is not running, removing from Redis.")
                        redis.removeServer(server.id)


                        val template = templateManager.getTemplate(server.templateName)
                            ?: throw IllegalStateException("Template ${server.templateName} not found for server ${server.serverName}")

                        if (template.dynamic != null) {
                            docker.deleteContainerInBackground(server.id, true)
                        }

                        val stoppedEvent = ServerUpdateStateEvent(
                            server.id,
                            ServerState.GONE,
                        )

                        redis.publishEvent(RedisNames.SERVERS, stoppedEvent)
                    }
                }

                val templates = templateManager.listTemplates()
                templates.filter { it.dynamic != null }.forEach { template ->
                    val runningServers = servers.count { it.templateName == template.name && docker.getContainerState(it.id)?.running == true }
                    val queuedServers = serverQueue[template] ?: 0

                    val minServers = template.dynamic?.minServers ?: 0

                    if (runningServers + queuedServers < minServers) {
                        repeat(minServers - runningServers) {
                            createServer(template, ServerData())
                        }
                    }
                }

                delay(60000)
            }
        }
    }
}