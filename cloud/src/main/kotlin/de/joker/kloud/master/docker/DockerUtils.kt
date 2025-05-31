package de.joker.kloud.master.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import java.net.ServerSocket

object DockerUtils {
    fun pullImage(client: DockerClient, image: String, tag: String = "latest"): Boolean {
        var success = true
        val callback = object : PullImageResultCallback() {
            override fun onNext(item: PullResponseItem?) {
                super.onNext(item)
                if (item == null) return
                if (!item.isPullSuccessIndicated) success = false
            }
        }

        client.pullImageCmd("$image:$tag").exec(callback)
        callback.awaitCompletion()
        return success
    }

    fun findClosestPortTo25565(range: Int = 100): Int? {
        val targetPort = 25565

        // Check ports around 25565, alternating above and below
        for (offset in 0..range) {
            val candidates = listOf(targetPort + offset, targetPort - offset).distinct().filter { it in 1024..65535 }
            for (port in candidates) {
                try {
                    ServerSocket(port).use {
                        return port // Port is available
                    }
                } catch (_: Exception) {
                    // Port is taken, continue searching
                }
            }
        }

        return null // No port found in range
    }
}