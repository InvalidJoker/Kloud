package de.joker.kloud.master.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import java.net.ServerSocket

object DockerUtils {
    val portsFound = mutableListOf<Int>()
    fun pullImage(client: DockerClient, image: String, tag: String = "latest"): Boolean {
        var success = true
        val callback = object : PullImageResultCallback() {
            override fun onNext(item: PullResponseItem?) {
                super.onNext(item)
                if (item == null) return
                if (!item.isPullSuccessIndicated && item.errorDetail != null) {
                    success = false
                    println("Error pulling image: ${item.errorDetail?.message}")
                }
            }
        }

        client.pullImageCmd("$image:$tag").exec(callback)
        callback.awaitCompletion()
        return success
    }

    fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true } // Try to open the port
        } catch (e: Exception) {
            false // Port is already in use
        }
    }

    fun findClosestPortTo25565(range: Int = 100): Int? {
        val targetPort = 25565

        // Check ports around 25565, alternating above and below
        for (offset in 0..range) {
            val candidates = listOf(targetPort + offset, targetPort - offset).distinct()
                .filter { it in 1024..65535 && !portsFound.contains(it) }
            for (port in candidates) {
                if (isPortAvailable(port)) {
                    portsFound.add(port) // Mark this port as found
                    return port // Return the first available port found
                }
            }
        }

        return null // No port found in range
    }
}