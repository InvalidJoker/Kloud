package de.joker.kloud.master.docker

enum class ServerType(
    val image: String,
) {
    PROXY("itzg/mc-proxy"),
    SERVER("itzg/minecraft-server")
}