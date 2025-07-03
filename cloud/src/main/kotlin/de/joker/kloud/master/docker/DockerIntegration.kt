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
import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import de.joker.kloud.master.Config
import de.joker.kloud.master.secret.SecretManager
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.master.template.image.ImageManager
import de.joker.kloud.shared.InternalApi
import de.joker.kloud.shared.events.ServerState
import de.joker.kloud.shared.server.SerializableServer
import de.joker.kloud.shared.server.ServerData
import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.templates.Template
import de.joker.kloud.shared.utils.logger
import de.joker.kutils.core.extensions.ifTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File


class DockerIntegration : KoinComponent {
    lateinit var dockerClient: DockerClient

    val redis: RedisConnector by inject()
    val secretManager: SecretManager by inject()
    val templates: TemplateManager by inject()
    val images: ImageManager by inject()

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

        val kloudNetwork = dockerClient.listNetworksCmd()
            .withNameFilter("kloud_network")
            .exec()
            .firstOrNull()

        if (kloudNetwork == null) {
            dockerClient.createNetworkCmd()
                .withName("kloud_network")
                .withDriver("bridge")
                .exec()
            logger.info("Created kloud_network for containers.")
        } else {
            logger.info("kloud_network already exists.")
        }

        images.listImages().forEach {
            val success = DockerUtils.pullImage(dockerClient, it.image, it.defaultVersion)
            if (success) {
                logger.info("Image ${it.image} pulled successfully.")
            } else {
                logger.error("Failed to pull image ${it.image}.")
            }
        }

        val templateVersions = templates.listTemplates().map {
            val image = images.getImage(it.build.image) ?: throw IllegalStateException("Image ${it.build.image} not found for template ${it.name}")

            image to it.build.imageVersion
        }

        templateVersions.forEach { (image, version) ->
            val success = DockerUtils.pullImage(dockerClient, image.image, version)
            if (success) {
                logger.info("Template image ${image.image} pulled successfully.")
            } else {
                logger.error("Failed to pull template image ${image.image}.")
            }
        }
    }

    @Synchronized
    fun ensureVolumeExists(name: String) {
        if (!dockerClient.listVolumesCmd().exec().volumes.any { it.name == name }) {
            dockerClient.createVolumeCmd().withName(name).exec()
            logger.info("Created volume: $name")
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

    fun restartContainerInBackground(containerId: String, onRestarted: (() -> Unit)? = null) {
        scope.launch {
            restartContainerBlocking(containerId) {
                onRestarted?.invoke()
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

    fun restartContainerBlocking(containerId: String, onRestarted: (() -> Unit)? = null) {
        dockerClient.restartContainerCmd(containerId).exec()
        onRestarted?.invoke()
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
            .filter { it.labels["kloud-template"] != null }
            .mapNotNull { container ->
                runCatching {
                    dockerClient.inspectContainerCmd(container.id).withContainerId(container.id).exec()
                }.getOrNull()
            }
    }

    fun deleteServerDirectory(server: SerializableServer) {
        val serverDir = File("./running/${server.internalId}")
        if (serverDir.exists()) {
            serverDir.deleteRecursively()
            logger.info("Deleted server directory for server: ${server.internalId}")
        }
    }

    fun movePaperPlaceholders(path: File) {
        val paperGlobalFile = File(path, "config/paper-global.yml")

        val yamlParser = Yaml(
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
            }
        )

        val current = mutableMapOf<String, Any>()

        if (paperGlobalFile.exists()) {
            val paperGlobal = yamlParser.load<Map<String, Any>>(paperGlobalFile.inputStream())

            if (paperGlobal != null) {
                current.putAll(paperGlobal)
            }
        }

        current["proxies"] = mapOf(
            "bungee-cord" to mapOf(
                "online-mode" to true
            ),
            "proxy-protocol" to false,
            "velocity" to mapOf(
                "enabled" to true,
                "online-mode" to true,
                "secret" to secretManager.getSecret()
            )
        )

        paperGlobalFile.writeText(yamlParser.dump(current))
    }


    fun moveVelocityPlaceholders(path: File) {
        val velocityGlobalFile = File(path, "velocity.toml")

        val text = velocityGlobalFile.readText()

        val data = Toml().read(text)

        val current = data.toMap().toMutableMap()

        current["online-mode"] = true
        current["player-info-forwarding-mode"] = "modern"
        current["prevent-client-proxy-connections"] = false
        current["forwarding-secret-file"] = "forwarding.secret"

        val tomlWriter = TomlWriter.Builder()
            .indentValuesBy(2)
            .indentTablesBy(4)
            .padArrayDelimitersBy(3)
            .build()

        velocityGlobalFile.writeText(tomlWriter.write(current))
    }

    fun attachContainerLogs(containerId: String, onLog: (String) -> Unit) {
        dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .withTailAll()
            .exec(object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                override fun onNext(frame: com.github.dockerjava.api.model.Frame) {
                    val log = String(frame.payload)
                    onLog(log)
                }
            })
    }

    @OptIn(InternalApi::class)
    fun createContainer(
        id: String,
        template: Template,
        serverName: String,
        onCreated: ((container: CreateContainerResponse, port: Int) -> Unit)? = null,
        onFinished: ((container: CreateContainerResponse, port: Int) -> Unit)? = null,
    ) {

        val img = images.getImage(template.build.image)
            ?: throw IllegalStateException("Image ${template.build.image} not found for template ${template.name}")

        val free = if (template.dynamic == null && template.forcedPort != null) {
            if (DockerUtils.isPortAvailable(template.forcedPort!!)) {
                template.forcedPort!!
            } else {
                DockerUtils.findClosestPortTo25565() ?: throw IllegalStateException("No free port found for container ${template.name} with forced port ${template.forcedPort}")
            }

        } else {
            DockerUtils.findClosestPortTo25565()
                ?: throw IllegalStateException("No free port found for container ${template.name}")
        }

        val ports = mapOf( // TODO: make this dynamic
            free to img.defaultInternalPort
        ).map { (hostPort, containerPort) ->
            PortBinding.parse("$hostPort:$containerPort")
        }

        val redisHost = if (Config.redisHost == "localhost") {
            "host.docker.internal"
        } else {
            Config.redisHost
        }

        val fullEnv = img.environment.toMutableMap().apply {
            putAll(template.environment)
            putIfAbsent("KLOUD_TEMPLATE", template.name)
            putIfAbsent("EULA", "TRUE")
            putIfAbsent("KLOUD_SERVER_NAME", serverName)
            putIfAbsent("KLOUD_SERVER_PORT", free.toString())
            putIfAbsent("KLOUD_REDIS_HOST", redisHost)
            putIfAbsent("KLOUD_REDIS_PORT", Config.redisPort.toString())
            putIfAbsent("KLOUD_API_TOKEN", Config.apiToken)
            putIfAbsent("KLOUD_API_PORT", Config.BACKEND_PORT.toString())
            putIfAbsent("KLOUD_ID", id)
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
            var bind: Bind

            val dir = if (isPersistent) {
                val persistentVolumePath = "./templates/$volumeName"
                val file = File(persistentVolumePath)

                file
            } else {
                val runningDirectory = File("./running/${id}")
                if (runningDirectory.exists()) {
                    runningDirectory.deleteRecursively()
                }

                runningDirectory.mkdirs()

                val directories = mutableListOf(
                    template.name,
                    "dynamic_${template.type.name.lowercase()}", // all dynamic from the same type
                )

                directories.addAll(template.dynamic!!.extraDirectories)

                // copy template files to running directory
                directories.forEach { dirName ->
                    val templateDir = File("./templates/$dirName")

                    if (templateDir.exists()) {
                        templateDir.copyRecursively(runningDirectory, true)
                    } else {
                        templateDir.mkdirs()
                    }
                }

                runningDirectory
            }

            if (template.type == ServerType.PROXIED_SERVER) {
                movePaperPlaceholders(dir)
            } else if (template.type == ServerType.PROXY) {
                moveVelocityPlaceholders(dir)
            }

            // Create volume if it doesn't exist
            ensureVolumeExists(volumeName)

            val volume = Volume(dataLocation)
            bind = Bind(dir.absolutePath, volume)


            val secretVolumeName = template.name
            var secretBind: Bind? = null



            if (template.type == ServerType.PROXY) {
                // if there already is a forwarding.secret file, in current mount delete it
                val secretFileExists = File(dir, "forwarding.secret")

                if (secretFileExists.exists()) {
                    secretFileExists.delete()
                }

                val secretFile = secretManager.getFullSecretPath().absolutePath

                // Create volume if it doesn't exist
                if (!dockerClient.listVolumesCmd().exec().volumes.any { it.name == secretVolumeName }) {
                    dockerClient.createVolumeCmd()
                        .withName(secretVolumeName)
                        .exec()
                    logger.info("Created secret volume: $secretVolumeName")
                } else {
                    logger.info("Secret volume already exists: $secretVolumeName")
                }

                val secretVolume = Volume("$dataLocation/forwarding.secret")
                secretBind = Bind(secretFile, secretVolume)
            }

            val binds = mutableListOf<Bind>().apply {
                add(bind)
                if (secretBind != null) add(secretBind)
            }

            val containerCmd = dockerClient.createContainerCmd("${img.image}:${template.build.imageVersion}")
                .withName(serverName)
                .withTty(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withLabels(mapOf("kloud-template" to template.name))
                .withEnv(fullEnv.map { "${it.key}=${it.value}" })

            containerCmd.withHostConfig(
                HostConfig.newHostConfig()
                    .withPortBindings(*ports.toTypedArray())
                    .withAutoRemove(true)
                    .withBinds(*binds.toTypedArray())
                    .ifTrue({ template.type != ServerType.STANDALONE_SERVER }) { e ->
                        e.withNetworkMode("kloud_network")
                    }
            )

            val container = containerCmd.exec()

            onCreated?.invoke(container, free)

            val line = img.startedMessageRegexPattern

            startContainerInBackground(container.id) {
                scope.launch {
                    attachContainerLogs(container.id) { log ->
                        if (log.contains(line)) {
                            val server = redis.getServerByInternal(id)
                                ?: throw IllegalStateException("Server with ID $id not found in Redis after starting container ${template.name}")

                            redis.changeServerState(server, ServerState.RUNNING)
                            logger.info("Container ${template.name} started successfully with ID ${container.id}")
                        }
                    }
                }

                onFinished?.invoke(container, free)
            }
            logger.info("Container ${template.name} created with ID ${container.id}")
        }
    }
}