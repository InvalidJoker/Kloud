package de.joker.kloud.master

import de.joker.kloud.master.backend.CloudBackend
import de.joker.kloud.master.other.SecretManager
import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.master.docker.DockerIntegration
import de.joker.kloud.master.redis.RedisConnector
import dev.fruxz.ascend.json.globalJson
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
    fun start() {
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

        // add shutdown hook to close resources
        Runtime.getRuntime().addShutdownHook(Thread {
            // TODO: delete all servers
            redis.redisAdapter.close()
        })

        while (true) {
            val input = readLine()
            if (input == null) continue

            when (input.split(" ").firstOrNull()?.lowercase()) {
                "exit", "quit" -> {
                    println("Exiting Kloud instance...")
                    break
                }
                "start" -> {
                    val templateName = input.split(" ").getOrNull(1)
                    if (templateName != null) {
                        val templateToStart = template.getTemplate(templateName)
                        if (templateToStart != null) {
                            serverManager.createServer(templateToStart)
                        } else {
                            println("Template '$templateName' not found.")
                        }
                    } else {
                        println("Please provide a template name to start.")
                    }
                }
            }
        }
    }
}