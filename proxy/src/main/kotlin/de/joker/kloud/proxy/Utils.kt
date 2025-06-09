package de.joker.kloud.proxy

import java.net.InetAddress

fun getLocalIP(): String {
    return InetAddress.getLocalHost().hostAddress ?: "localhost"
}
fun main() {
    println(getLocalIP())
}

