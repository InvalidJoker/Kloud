package de.joker.kloud.proxy

import de.joker.kutils.core.tools.Environment

object Config {
    val redisHost = Environment.getString("KLOUD_REDIS_HOST") ?: "localhost"
    val redisPort = Environment.getIntOrDefault("KLOUD_REDIS_PORT", 6379)
}