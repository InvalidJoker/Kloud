package de.joker.kloud.shared

import de.joker.kloud.shared.common.RedisServer
import de.joker.kloud.shared.events.IEvent
import dev.fruxz.ascend.json.globalJson

abstract class RedisWrapper(
    val redisAdapter: RedisCalls
) {
    fun connect() {
        redisAdapter.connect()
    }

    fun getAllServers(): List<RedisServer> {
        return try {
            redisAdapter.getHash("servers").values.map {
                globalJson.decodeFromString<RedisServer>(it)
            }
        } catch (e: Exception) {
            logger.error("Failed to get all servers", e)
            emptyList()
        }
    }

    fun getServer(containerId: String): RedisServer? {
        return try {
            redisAdapter.getFromHash("servers", containerId)?.let {
                globalJson.decodeFromString<RedisServer>(it)
            }
        } catch (e: Exception) {
            logger.error("Failed to get server with ID: $containerId", e)
            null
        }
    }

    fun saveServer(server: RedisServer): Boolean {
        return try {
            val json = globalJson.encodeToString(server)
            redisAdapter.addToHash("servers", server.containerId, json)
            true
        } catch (e: Exception) {
            logger.error("Failed to save server: ${server.serverName}", e)
            false
        }
    }

    fun removeServer(containerId: String): Boolean {
        return try {
            redisAdapter.removeFromHash("servers", containerId)
            true
        } catch (e: Exception) {
            logger.error("Failed to remove server with ID: $containerId", e)
            false
        }
    }

    fun getLobbyServers(): List<RedisServer> {
        return getAllServers().filter { it.lobby }
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