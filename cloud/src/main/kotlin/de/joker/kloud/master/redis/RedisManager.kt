package de.joker.kloud.master.redis

import de.joker.kloud.master.Config
import de.joker.kloud.shared.RedisCalls
import de.joker.kloud.shared.RedisWrapper
import org.koin.core.component.KoinComponent

class RedisManager : KoinComponent, RedisWrapper(
    RedisCalls(
        Config.redisHost,
        Config.redisPort,
        listOf(ServerHandler())
    )
)