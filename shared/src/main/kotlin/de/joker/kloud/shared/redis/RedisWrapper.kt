package de.joker.kloud.shared.redis

import de.joker.kloud.shared.InternalApi
import de.joker.kloud.shared.events.IEvent
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
            redisAdapter.getHash("servers").values.map {
                globalJson.decodeFromString<SerializableServer>(it)
            }
        } catch (e: Exception) {
            logger.error("Failed to get all servers", e)
            emptyList()
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

    fun publishEvent(channel: String, event: IEvent): Boolean {
        return try {
            redisAdapter.emitEvent(channel, event)
            true
        } catch (e: Exception) {
            logger.error("Failed to publish event to channel: $channel", e)
            false
        }
    }

    fun publishEvent(channel: RedisNames, event: IEvent): Boolean {
        return publishEvent(channel.channel, event)
    }
}