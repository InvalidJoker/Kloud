package de.joker.kloud.master

import de.joker.kloud.master.backend.CloudBackend
import de.joker.kloud.master.server.ServerManager
import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.secret.SecretManager
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.master.template.image.ImageManager
import de.joker.kloud.shared.events.CloudStoppedEvent
import de.joker.kloud.shared.redis.RedisNames
import de.joker.kloud.shared.utils.logger
import dev.fruxz.ascend.json.globalJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.InternalSerializationApi
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger

object KloudInstance {

    @OptIn(InternalSerializationApi::class)
    suspend fun start() {
        val docker = DockerIntegration()
        val redis = RedisConnector()
        val image = ImageManager()
        val template = TemplateManager()
        val serverManager = ServerManager()
        val secretManager = SecretManager()
        val backend = CloudBackend()

        startKoin {
            slf4jLogger()
            modules(
                module {
                    single { docker }
                    single { redis }
                    single { image }
                    single { template }
                    single { serverManager }
                    single { secretManager }
                    single { backend }
                    single { globalJson }
                }
            )
        }

        secretManager.loadSecrets()

        image.loadImagesFromFile()

        template.loadTemplatesFromFile()

        redis.connect()
        docker.loadDockerClient()

        serverManager.startup()

        backend.start()

        suspendCancellableCoroutine { continuation ->
            Runtime.getRuntime().addShutdownHook(Thread {
                redis.emit(RedisNames.CLOUD, CloudStoppedEvent())
                serverManager.cleanupCurrent()
                continuation.resume(Unit) { cause, _, _ ->
                    logger.info("Server shutdown due to: $cause")
                }
            })
        }
    }
}