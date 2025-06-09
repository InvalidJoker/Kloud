package de.joker.kloud.shared

import de.joker.kloud.shared.events.IEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

abstract class RedisAdapter(
    val host: String,
    val port: Int,
    val channels: List<RedisHandler>
) {
    lateinit var jedisPool: JedisPool
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

    fun getHash(key: String): Map<String, String> {
        return jedisPool.resource.use { jedis ->
            val hash = jedis.hgetAll(key)
            logger.info("Retrieved hash '$key': $hash")
            hash
        }
    }

    fun addToHash(key: String, field: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.hset(key, field, value)
                logger.info("Added field '$field' with value '$value' to hash '$key'")
            }
        } catch (e: Exception) {
            logger.error("Failed to add field '$field' with value '$value' to hash '$key'", e)
        }
    }

    fun getFromHash(key: String, field: String): String? {
        return jedisPool.resource.use { jedis ->
            val value = jedis.hget(key, field)
            logger.info("Retrieved field '$field' from hash '$key': $value")
            value
        }
    }

    fun removeFromHash(key: String, field: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.hdel(key, field)
                logger.info("Removed field '$field' from hash '$key'")
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
            logger.info("Retrieved string for key '$key': $value")
            value
        }
    }

    fun setString(key: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.set(key, value)
                logger.info("Set string for key '$key' with value '$value'")
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