package de.joker.kloud.proxy.redis

import de.joker.kloud.proxy.Config
import de.joker.kloud.shared.RedisCalls
import de.joker.kloud.shared.RedisWrapper
import org.koin.core.component.KoinComponent

class RedisSubscriber : KoinComponent, RedisWrapper(
    RedisCalls(
        Config.redisHost,
        Config.redisPort,
        listOf(ServerHandler())
    )
)