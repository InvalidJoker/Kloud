package de.joker.kloud.shared.redis

import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.utils.eventJson
import de.joker.kloud.shared.utils.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisCalls(
    val host: String,
    val port: Int,
    val channels: List<RedisHandler>
) {
    private lateinit var jedisPool: JedisPool
    private lateinit var jedisPubSub: JedisPubSub

    val redisScope = CoroutineScope(Dispatchers.IO)

    @OptIn(InternalSerializationApi::class)
    fun connect() {
        logger.info("Connecting to Redis server...")

        val jedisPoolConfig = JedisPoolConfig()
        jedisPoolConfig.maxTotal = 50
        jedisPoolConfig.maxIdle = 5
        jedisPoolConfig.minIdle = 1
        jedisPoolConfig.testOnBorrow = true
        jedisPool = JedisPool(jedisPoolConfig, host, port)

        jedisPubSub = object : JedisPubSub() {
            override fun onMessage(channel: String?, message: String?) {
                if (channel == null || message.isNullOrEmpty()) return

                try {
                    val event = eventJson.decodeFromString(IEvent::class.serializer(), message)

                    val handler = channels.filter { it.channel == channel }

                    if (handler.isNotEmpty()) handler.forEach { it.handleEvent(event) }
                } catch (e: Exception) {
                    logger.error("Failed to parse event: ${e.message}")
                }

            }
        }

        if (channels.isNotEmpty()) {
            redisScope.launch {
                jedisPool.resource.use { jedis ->
                    jedis.subscribe(jedisPubSub, *channels.map { it.channel }.distinct().toTypedArray())
                }
            }
        }

        logger.info("Redis client initialized successfully.")
    }

    fun getHash(key: String): Map<String, String> {
        return jedisPool.resource.use { jedis ->
            val hash = jedis.hgetAll(key)
            hash
        }
    }

    fun addToHash(key: String, field: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.hset(key, field, value)
            }
        } catch (e: Exception) {
            logger.error("Failed to add field '$field' with value '$value' to hash '$key'", e)
        }
    }

    fun getFromHash(key: String, field: String): String? {
        return jedisPool.resource.use { jedis ->
            val value = jedis.hget(key, field)
            value
        }
    }

    fun removeFromHash(key: String, field: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.hdel(key, field)
            }
        } catch (e: Exception) {
            logger.error("Failed to remove field '$field' from hash '$key'", e)
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun emitEvent(channel: String, event: IEvent) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.publish(channel, eventJson.encodeToString(IEvent::class.serializer(), event))
            }
        } catch (e: Exception) {
            logger.error("Failed to publish event to channel '$channel'", e)
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun emitEvent(channel: RedisNames, event: IEvent) {
        emitEvent(channel.channel, event)
    }

    fun getString(key: String): String? {
        return jedisPool.resource.use { jedis ->
            val value = jedis.get(key)
            value
        }
    }

    fun setString(key: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.set(key, value)
            }
        } catch (e: Exception) {
            logger.error("Failed to set string for key '$key'", e)
        }
    }

    fun close() {
        logger.info("Closing Redis connection...")
        jedisPubSub.unsubscribe()
        jedisPool.close()
        logger.info("Redis connection closed.")
    }
}