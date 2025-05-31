package de.joker.kloud.master.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.PullResponseItem

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
}