package de.joker.kloud.master.core

import de.joker.kloud.master.data.Template
import de.joker.kloud.master.data.TemplateManager
import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.master.json
import de.joker.kloud.master.redis.RedisManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ServerManager : KoinComponent {
    val serverIds = mutableMapOf<String, Int>()
    fun startup() {
        cleanup()
        val templateManager: TemplateManager by inject()

        val templates = templateManager.listTemplates()

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

        servers.forEach { server ->
            val serverTemplate = template.getTemplate(server.templateName) ?: throw IllegalStateException("Template ${server.templateName} not found for server ${server.serverName}")

            if (serverTemplate.dynamic == null) {
                // static server, stop and delete it
                docker.stopContainerBlocking(server.containerId) {
                    docker.deleteContainerBlocking(server.containerId, false)
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
}