package de.joker.kloud.master

import de.joker.kloud.master.backend.CloudBackend
import de.joker.kloud.master.server.ServerManager
import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.secret.SecretManager
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
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

    private val kloudModules = listOf(
        module { single { DockerIntegration() } },
        module { single { RedisConnector() } },
        module { single { TemplateManager() } },
        module { single { ServerManager() } },
        module { single { SecretManager() } },
        module { single { CloudBackend() } },
        module { single { globalJson } },
    )

    @OptIn(InternalSerializationApi::class)
    suspend fun start() {
        startKoin {
            slf4jLogger()
            modules(
                kloudModules +
                        module {
                            single { globalJson }
                        }
            )
        }

        val redis: RedisConnector by inject(RedisConnector::class.java)
        val docker: DockerIntegration by inject(DockerIntegration::class.java)
        val template: TemplateManager by inject(TemplateManager::class.java)
        val serverManager: ServerManager by inject(ServerManager::class.java)
        val secretManager: SecretManager by inject(SecretManager::class.java)
        val backend: CloudBackend by inject(CloudBackend::class.java)

        secretManager.loadSecrets()

        template.loadTemplatesFromFile()

        redis.connect()
        docker.loadDockerClient()

        serverManager.startup()

        backend.start()

        suspendCancellableCoroutine { continuation ->
            Runtime.getRuntime().addShutdownHook(Thread {
                redis.publishEvent(RedisNames.CLOUD, CloudStoppedEvent())
                serverManager.cleanupCurrent()
                continuation.resume(Unit) { cause, _, _ ->
                    logger.info("Server shutdown due to: $cause")
                }
            })
        }
    }
}