package de.joker.kloud.shared.redis

import de.joker.kloud.shared.InternalApi
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.utils.logger
import de.joker.kutils.core.tools.Environment
import dev.fruxz.ascend.json.globalJson

abstract class RedisWrapper(
    val redisAdapter: RedisCalls
) {
    fun connect() {
        redisAdapter.connect()
    }

    fun getAllServers(): List<SerializableServer> {
        return try {
            redisAdapter.getHash("servers").values.mapNotNull {
                try {
                    globalJson.decodeFromString<SerializableServer>(it)
                } catch (e: Exception) {
                    logger.error("Failed to decode server from Redis", e)
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get all servers", e)
            emptyList()
        }
    }

    fun getServersByTemplate(templateName: String): List<SerializableServer> {
        return try {
            val servers = getAllServers()
            servers.filter { it.template.name.equals(templateName, ignoreCase = true) }
        } catch (e: Exception) {
            logger.error("Failed to get servers for template: $templateName", e)
            emptyList()
        }
    }

    fun getServerByName(name: String): SerializableServer? {
        return try {
            val servers = getAllServers()
            servers.find { it.serverName.equals(name, ignoreCase = true) }
        } catch (e: Exception) {
            logger.error("Failed to get server with name: $name", e)
            null
        }
    }

    fun getServerByInternal(internalId: String): SerializableServer? {
        return try {
            redisAdapter.getFromHash("servers", internalId)?.let {
                globalJson.decodeFromString<SerializableServer>(it)
            }
        } catch (e: Exception) {
            logger.error("Failed to get server with ID: $internalId", e)
            null
        }
    }

    fun getServerByContainer(containerId: String): SerializableServer? {
        return try {
            val servers = getAllServers()
            servers.find { it.containerId == containerId }
        } catch (e: Exception) {
            logger.error("Failed to get server with container ID: $containerId", e)
            null
        }
    }

    @InternalApi
    fun saveServer(server: SerializableServer): Boolean {
        return try {
            val json = globalJson.encodeToString(server)
            redisAdapter.addToHash("servers", server.internalId, json)
            true
        } catch (e: Exception) {
            logger.error("Failed to save server: ${server.serverName}", e)
            false
        }
    }

    @InternalApi
    fun removeServer(internalId: String): Boolean {
        return try {
            redisAdapter.removeFromHash("servers", internalId)
            true
        } catch (e: Exception) {
            logger.error("Failed to remove server with ID: $internalId", e)
            false
        }
    }

    fun getLobbyServers(): List<SerializableServer> {
        return getAllServers().filter { it.template.lobby }
    }

    fun getSelfServer(): SerializableServer? {
        val selfId = Environment.getString("KLOUD_ID")

        return if (selfId.isNullOrBlank()) {
            logger.warn("KLOUD_ID environment variable is not set or is empty.")
            null
        } else {
            getServerByInternal(selfId)
        }
    }

    @InternalApi
    fun changeServerState(internalId: String, newState: ServerState): Boolean {
        val server = getServerByInternal(internalId)
        if (server == null) {
            logger.error("Server with ID: $internalId not found.")
            return false
        }
        return changeServerState(server, newState)
    }

    @InternalApi
    fun changeServerState(server: SerializableServer, newState: ServerState): Boolean {
        return try {
            val stoppedEvent = ServerUpdateStateEvent(
                server,
                newState,
            )
            emit(RedisNames.SERVERS, stoppedEvent)
            true
        } catch (e: Exception) {
            logger.error("Failed to change state for server with ID: ${server.internalId}", e)
            false
        }
    }

    fun emit(channel: String, event: IEvent): Boolean {
        return try {
            redisAdapter.emitEvent(channel, event)
            true
        } catch (e: Exception) {
            logger.error("Failed to publish event to channel: $channel", e)
            false
        }
    }

    fun emit(channel: RedisNames, event: IEvent): Boolean {
        return emit(channel.channel, event)
    }
}