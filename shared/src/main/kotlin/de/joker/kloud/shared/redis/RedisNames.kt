package de.joker.kloud.shared.redis

enum class RedisNames(
    val channel: String,
) {
    CLOUD("cloud"),
    SERVERS("servers"),
}