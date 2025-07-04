package de.joker.kloud.master

import de.joker.kutils.core.tools.Environment

object Config {
    const val BACKEND_PORT = 8080
    val redisHost = Environment.getString("REDIS_HOST") ?: "localhost"
    val redisPort = Environment.getIntOrDefault("REDIS_PORT", 6379)
    val apiToken = Environment.getString("API_TOKEN") ?: "default_token"
}