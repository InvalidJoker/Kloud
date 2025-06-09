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
import com.github.dockerjava.okhttp.OkDockerHttpClient
import de.joker.kloud.master.Config
import de.joker.kloud.master.template.Template
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.logger
import de.joker.kloud.master.redis.RedisManager
import de.joker.kloud.shared.RedisNames
import de.joker.kloud.shared.common.ServerType
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import de.joker.kutils.core.extensions.ifTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.collections.mapNotNull

class DockerManager : KoinComponent {
    lateinit var dockerClient: DockerClient

    val scope = CoroutineScope(Dispatchers.IO)

    fun loadDockerClient() {
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        val okHttpClient = OkDockerHttpClient.Builder()
            .dockerHost(dockerConfig.dockerHost)
            .sslConfig(dockerConfig.sslConfig)
            .build()

        dockerClient = dockerConfig.run {
            DockerClientBuilder.getInstance(this)
                .withDockerHttpClient(okHttpClient)
                .build()
        }

        runCatching {
            dockerClient.pingCmd().exec()
        }.onFailure { throw IllegalStateException("Docker client could not connect to Docker daemon. Is Docker running?") }

        logger.info("Docker client initialized successfully.")

        val templateManager: TemplateManager by inject()

        val kCludNetwork = dockerClient.listNetworksCmd()
            .withNameFilter("kcloud_network")
            .exec()
            .firstOrNull()

        if (kCludNetwork == null) {
            dockerClient.createNetworkCmd()
                .withName("kcloud_network")
                .withDriver("bridge")
                .exec()
            logger.info("Created kcloud_network for containers.")
        } else {
            logger.info("kcloud_network already exists.")
        }

        templateManager.listTemplates().forEach {
            val success = DockerUtils.pullImage(dockerClient, it.image)
            if (success) {
                logger.info("Image ${it.image} pulled successfully.")
            } else {
                logger.error("Failed to pull image ${it.image}.")
            }
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
    fun getContainers(): List<InspectContainerResponse> {
        return dockerClient.listContainersCmd()
            .withShowAll(true)
            .exec()
            .mapNotNull { container ->
                runCatching {
                    dockerClient.inspectContainerCmd(container.id).exec()
                }.getOrNull()
            }
    }

    fun createContainer(
        template: Template,
        serverName: String,
        onFinished: ((container: CreateContainerResponse, port: Int) -> Unit)? = null
    ) {
        val redis: RedisManager by inject()
        val free = DockerUtils.findClosestPortTo25565() ?: throw IllegalStateException("No free port found for container ${template.name}")

        val ports = mapOf( // TODO: make this dynamic
            free to 25565
        ).map { (hostPort, containerPort) ->
            PortBinding.parse("$hostPort:$containerPort")
        }

        val redisHost = if (Config.redisHost == "localhost") {
            "host.docker.internal"
        } else {
            Config.redisHost
        }

        val fullEnv = template.environment.toMutableMap().apply {
            putIfAbsent("KLOUD_TEMPLATE", template.name)
            putIfAbsent("EULA", "TRUE")
            putIfAbsent("KLOUD_SERVER_NAME", serverName)
            putIfAbsent("KLOUD_SERVER_PORT", free.toString())
            putIfAbsent("KLOUD_REDIS_HOST", redisHost)
            putIfAbsent("KLOUD_REDIS_PORT", Config.redisPort.toString())
        }

        if (template.type == ServerType.PROXIED_SERVER) {
            fullEnv["ONLINE_MODE"] = "FALSE" // Disable online mode for proxied servers
        }

        val dataLocation = if (template.type == ServerType.PROXY) {
            "/server"
        } else {
            "/data"
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

                volume = Volume(dataLocation)
                bind = Bind(file, volume)
            } else {
                volume = null
                bind = null
            }

            val containerCmd = dockerClient.createContainerCmd(template.image)
                .withName(serverName)
                .withLabels(mapOf("kloud-template" to template.name))
                .withEnv(fullEnv.map { "${it.key}=${it.value}" })

            if (volume != null) {
                containerCmd.withVolumes(volume)
                    .withHostConfig(
                        HostConfig.newHostConfig()
                            .withPortBindings(*ports.toTypedArray())
                            .withBinds(bind)
                            .withAutoRemove(true)
                            .ifTrue({ template.type != ServerType.STANDALONE_SERVER }) { e ->
                                e.withNetworkMode("kcloud_network")
                            }
                    )
            } else {
                containerCmd.withHostConfig(
                    HostConfig.newHostConfig()
                        .withPortBindings(*ports.toTypedArray())
                        .withAutoRemove(true)
                        .ifTrue({ template.type != ServerType.STANDALONE_SERVER }) { e ->
                            e.withNetworkMode("kcloud_network")
                        }
                )
            }

            val container = containerCmd.exec()

            if (!isPersistent) {
                val filePaths = template.getFilePaths()

                filePaths.forEach { path ->
                    copyFilesToContainer(
                        container.id,
                        path,
                        dataLocation
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
                onFinished?.invoke(container, free)
            }
            logger.info("Container ${template.name} created with ID ${container.id}")
        }
    }
}