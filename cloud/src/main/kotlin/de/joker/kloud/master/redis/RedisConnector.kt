package de.joker.kloud.master.redis

import de.joker.kloud.master.Config
import de.joker.kloud.shared.redis.RedisCalls
import de.joker.kloud.shared.redis.RedisWrapper
import org.koin.core.component.KoinComponent

class RedisConnector : KoinComponent, RedisWrapper(
    RedisCalls(
        Config.redisHost,
        Config.redisPort,
        listOf()
    )
)