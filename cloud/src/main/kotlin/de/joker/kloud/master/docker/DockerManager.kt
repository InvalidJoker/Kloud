package de.joker.kloud.master.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import de.joker.kloud.master.data.Template
import de.joker.kloud.master.logger
import de.joker.kloud.master.redis.RedisManager
import de.joker.kloud.master.redis.RedisNames
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DockerManager : KoinComponent {
    lateinit var dockerClient: DockerClient

    val scope = CoroutineScope(Dispatchers.IO)

    fun loadDockerClient() {
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        dockerClient = dockerConfig.run {
            DockerClientBuilder.getInstance(this).build()
        }

        runCatching {
            dockerClient.pingCmd().exec()
        }.onFailure { throw IllegalStateException("Docker client could not connect to Docker daemon. Is Docker running?") }

        logger.info("Docker client initialized successfully.")

        scope.launch {
            ServerType.entries.forEach { serverType ->
                DockerUtils.pullImage(
                    dockerClient,
                    serverType.image
                )
            }

            logger.info("Docker images pulled successfully.")
        }
    }

    fun copyFilesToContainer(
        containerId: String,
        sourcePath: String,
        targetPath: String
    ) {
        scope.launch {
            dockerClient.copyArchiveToContainerCmd(containerId)
                .withHostResource(sourcePath)
                .withRemotePath(targetPath)
                .exec()
            logger.info("Files copied to container $containerId from $sourcePath to $targetPath")
        }
    }

    fun startContainerInBackground(containerId: String, onStarted: (() -> Unit)? = null) {
        scope.launch {
            startContainerBlocking(containerId) {
                onStarted?.invoke()
            }
        }
    }

    fun stopContainerInBackground(containerId: String, onStopped: (() -> Unit)? = null) {
        scope.launch {
            stopContainerBlocking(containerId) {
                onStopped?.invoke()
            }
        }
    }

    fun deleteContainerInBackground(containerId: String, force: Boolean = true, onDeleted: (() -> Unit)? = null) {
        scope.launch {
            deleteContainerBlocking(containerId, force) {
                onDeleted?.invoke()
            }
        }
    }

    fun startContainerBlocking(containerId: String, onStarted: (() -> Unit)? = null) {
        dockerClient.startContainerCmd(containerId).exec()
        logger.info("Container $containerId started successfully.")
        onStarted?.invoke()
    }

    fun stopContainerBlocking(containerId: String, onStopped: (() -> Unit)? = null) {
        dockerClient.stopContainerCmd(containerId).exec()
        logger.info("Container $containerId stopped successfully.")
        onStopped?.invoke()
    }

    fun deleteContainerBlocking(containerId: String, force: Boolean = true, onDeleted: (() -> Unit)? = null) {
        dockerClient.removeContainerCmd(containerId)
            .withForce(force)
            .exec()
        logger.info("Container $containerId deleted successfully.")
        onDeleted?.invoke()
    }

    fun getContainerState(containerId: String): InspectContainerResponse.ContainerState? {
        return try {
            dockerClient.inspectContainerCmd(containerId).exec().state
        } catch (e: Exception) {
            logger.error("Failed to inspect container $containerId: ${e.message}")
            null
        }
    }

    fun createContainer(
        template: Template,
        serverName: String,
        onFinished: ((container: CreateContainerResponse) -> Unit)? = null
    ) {
        val redis: RedisManager by inject()
        val free = DockerUtils.findClosestPortTo25565() ?: throw IllegalStateException("No free port found for container ${template.name}")

        val ports = mapOf( // TODO: make this dynamic
            free to 25565
        ).map { (hostPort, containerPort) ->
            PortBinding.parse("$hostPort:$containerPort")
        }

        val fullEnv = template.environment.toMutableMap().apply {
            putIfAbsent("KLOUD_TEMPLATE", template.name)
            putIfAbsent("EULA", "TRUE")
        }

        val doesServerExist = getContainerState(serverName) != null

        if (doesServerExist) {
            logger.warn("Container with name $serverName already exists. Please choose a different name.")
            return
        }

        scope.launch {
            val isPersistent = template.dynamic == null
            val volumeName = template.name
            val volume: Volume?
            val bind: Bind?

            if (isPersistent) {
                val persistentVolumePath = "./templates/$volumeName"
                val file = File(persistentVolumePath).absolutePath

                // Create volume if it doesn't exist
                if (!dockerClient.listVolumesCmd().exec().volumes.any { it.name == volumeName }) {
                    dockerClient.createVolumeCmd()
                        .withName(volumeName)
                        .exec()
                    logger.info("Created volume: $volumeName")
                } else {
                    logger.info("Volume already exists: $volumeName")
                }

                volume = Volume("/data")
                bind = Bind(file, volume)
            } else {
                volume = null
                bind = null
            }

            val containerCmd = dockerClient.createContainerCmd(template.image)
                .withName(serverName)
                .withEnv(fullEnv.map { "${it.key}=${it.value}" })

            if (volume != null) {
                containerCmd.withVolumes(volume)
                    .withHostConfig(
                        HostConfig.newHostConfig()
                            .withPortBindings(*ports.toTypedArray())
                            .withBinds(bind)
                    )
            } else {
                containerCmd.withHostConfig(
                    HostConfig.newHostConfig()
                        .withPortBindings(*ports.toTypedArray())
                )
            }

            val container = containerCmd.exec()

            if (!isPersistent) {
                val filePaths = template.getFilePaths()

                filePaths.forEach { path ->
                    copyFilesToContainer(
                        container.id,
                        path,
                        "/data"
                    )
                }
            }

            val event = ServerUpdateStateEvent(
                serverId = container.id,
                state = ServerState.STARTING
            )
            redis.emitEvent(RedisNames.SERVERS, event)

            startContainerInBackground(container.id) {
                val startedEvent = ServerUpdateStateEvent(
                    serverId = container.id,
                    state = ServerState.RUNNING
                )
                redis.emitEvent(RedisNames.SERVERS, startedEvent)
                onFinished?.invoke(container)
            }
            logger.info("Container ${template.name} created with ID ${container.id}")
        }
    }
}