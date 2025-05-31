package de.joker.kloud.master.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import de.joker.kloud.master.logger
import org.koin.core.component.KoinComponent

class DockerManager : KoinComponent {
    lateinit var dockerClient: DockerClient

    fun loadDockerClient() {
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        dockerClient = dockerConfig.run {
            DockerClientBuilder.getInstance(this).build()
        }

        runCatching {
            dockerClient.pingCmd().exec()
        }.onFailure { throw IllegalStateException("Docker client could not connect to Docker daemon. Is Docker running?") }

        logger.info("Docker client initialized successfully.")

        pullImages()

        logger.info("Docker images pulled successfully.")
    }

    fun pullImages() {
        ServerType.entries.forEach { serverType ->
            DockerUtils.pullImage(
                dockerClient,
                serverType.image
            )
        }
    }

    fun copyFilesToContainer(
        containerId: String,
        sourcePath: String,
        targetPath: String
    ) {
        dockerClient.copyArchiveToContainerCmd(containerId)
            .withHostResource(sourcePath)
            .withRemotePath(targetPath)
            .exec()
        logger.info("Files copied to container $containerId from $sourcePath to $targetPath")
    }

    fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
        logger.info("Container $containerId started successfully.")
    }

    fun stopContainer(containerId: String) {
        dockerClient.stopContainerCmd(containerId).exec()
        logger.info("Container $containerId stopped successfully.")
    }
}