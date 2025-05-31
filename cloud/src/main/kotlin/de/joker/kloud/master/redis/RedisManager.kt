package de.joker.kloud.master.redis

import de.joker.kloud.master.logger
import de.joker.kloud.shared.RedisHandler
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.eventJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisManager : KoinComponent {
    lateinit var jedisPool: JedisPool
    private lateinit var jedisPubSub: JedisPubSub

    val redisScope = CoroutineScope(Dispatchers.IO)

    @OptIn(InternalSerializationApi::class)
    fun connect() {
        logger.info("Connecting to Redis server...")

        val host = "localhost" // Replace with your Redis host
        val port = 6379 // Replace with your Redis port

        val jedisPoolConfig = JedisPoolConfig()
        jedisPoolConfig.maxTotal = 50
        jedisPoolConfig.maxIdle = 5
        jedisPoolConfig.minIdle = 1
        jedisPoolConfig.testOnBorrow = true
        jedisPool = JedisPool(jedisPoolConfig, host, port)

        val channels = listOf<RedisHandler>(
            ServerHandler()
        )
        jedisPubSub = object : JedisPubSub() {
            override fun onMessage(channel: String?, message: String?) {
                if (channel == null || message.isNullOrEmpty()) return

                try {
                    val event = eventJson.decodeFromString(IEvent::class.serializer(), message)

                    logger.info("Received event on channel $channel: $event")

                    val handler = channels.find { it.channel == channel }

                    if (handler != null) {
                        handler.handleEvent(event)
                    } else {
                        logger.warn("No handler found for channel $channel")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse event: ${e.message}")
                }

            }
        }

        redisScope.launch {
            jedisPool.resource.use { jedis ->
                jedis.subscribe(jedisPubSub, *channels.map { it.channel }.toTypedArray())
            }
        }

        logger.info("Redis client initialized successfully.")
    }

    fun addToList(key: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.rpush(key, value)
                logger.info("Added value '$value' to list '$key'")
            }
        } catch (e: Exception) {
            logger.error("Failed to add value '$value' to list '$key'", e)
        }
    }

    fun getList(key: String): List<String> {
        return jedisPool.resource.use { jedis ->
            val list = jedis.lrange(key, 0, -1) ?: emptyList()
            logger.info("Retrieved list '$key': $list")
            list
        }

    }

    fun removeFromList(key: String, value: String) {
        runCatching {
            jedisPool.resource.use { jedis ->
                jedis.lrem(key, 0, value)
                logger.info("Removed value '$value' from list '$key'")
            }
        }
    }

    fun close() {
        logger.info("Closing Redis connection...")
        jedisPubSub.unsubscribe()
        jedisPool.close()
        logger.info("Redis connection closed.")
    }
}