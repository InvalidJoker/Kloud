package de.joker.kloud.proxy.redis

import de.joker.kloud.proxy.Config
import de.joker.kloud.shared.RedisAdapter
import org.koin.core.component.KoinComponent

class RedisSubscriber : KoinComponent, RedisAdapter(
    Config.redisHost,
    Config.redisPort,
    listOf(ServerHandler())
)