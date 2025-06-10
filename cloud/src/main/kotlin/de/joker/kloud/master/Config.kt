package de.joker.kloud.master

import de.joker.kutils.core.tools.Environment

object Config {
    val redisHost = Environment.getString("REDIS_HOST") ?: "localhost"
    val redisPort = Environment.getIntOrDefault("REDIS_PORT", 6379)
    val backendPort = Environment.getIntOrDefault("BACKEND_PORT", 8080)
}