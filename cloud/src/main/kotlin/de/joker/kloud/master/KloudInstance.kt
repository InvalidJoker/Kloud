package de.joker.kloud.master

import de.joker.kloud.master.core.SecretManager
import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.master.redis.RedisManager
import dev.fruxz.ascend.json.globalJson
import kotlinx.serialization.InternalSerializationApi
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger

object KloudInstance {

    val dockerModule = module {
        single { DockerManager() }
    }

    val redisModule = module {
        single { RedisManager() }
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
                module {
                    single { globalJson }
                }
            )
        }

        val redis: RedisManager by inject(RedisManager::class.java)
        val docker: DockerManager by inject(DockerManager::class.java)
        val template: TemplateManager by inject(TemplateManager::class.java)
        val serverManager: ServerManager by inject(ServerManager::class.java)
        val secretManager: SecretManager by inject(SecretManager::class.java)

        secretManager.loadSecrets()

        template.loadTemplatesFromFile()

        redis.connect()
        docker.loadDockerClient()

        serverManager.startup()

        // add shutdown hook to close resources
        Runtime.getRuntime().addShutdownHook(Thread {
            serverManager.cleanup {
                redis.close()
            }
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