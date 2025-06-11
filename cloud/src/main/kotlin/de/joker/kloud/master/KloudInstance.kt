package de.joker.kloud.master

import de.joker.kloud.master.backend.CloudBackend
import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.other.SecretManager
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.utils.logger
import dev.fruxz.ascend.json.globalJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.InternalSerializationApi
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger

object KloudInstance {

    val dockerModule = module {
        single { DockerIntegration() }
    }

    val redisModule = module {
        single { RedisConnector() }
    }

    val templateModule = module {
        single { TemplateManager() }
    }

    val serverModule = module {
        single { ServerManager() }
    }

    val secretModule = module {
        single { SecretManager() }
    }

    val backendModule = module {
        single { CloudBackend() }
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun start() {
        startKoin {
            slf4jLogger()
            modules(
                dockerModule,
                redisModule,
                templateModule,
                serverModule,
                secretModule,
                backendModule,
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
                redis.redisAdapter.close()
                continuation.resume(Unit) { cause, _, _ ->
                    logger.info("Server shutdown due to: $cause")
                }
            })
        }
    }
}