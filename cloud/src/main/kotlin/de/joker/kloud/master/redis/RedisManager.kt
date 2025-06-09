package de.joker.kloud.master.redis

import de.joker.kloud.master.Config
import de.joker.kloud.shared.RedisAdapter
import org.koin.core.component.KoinComponent

class RedisManager : KoinComponent, RedisAdapter(
    Config.redisHost,
    Config.redisPort,
    listOf(ServerHandler())
)