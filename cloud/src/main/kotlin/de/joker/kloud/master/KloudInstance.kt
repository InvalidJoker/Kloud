package de.joker.kloud.master

import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.data.TemplateManager
import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.master.redis.RedisManager
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

    @OptIn(InternalSerializationApi::class)
    fun start() {
        startKoin {
            slf4jLogger()
            modules(
                dockerModule,
                redisModule,
                templateModule,
                serverModule,
                module {
                    single { json }
                }
            )
        }

        val redis: RedisManager by inject(RedisManager::class.java)
        val docker: DockerManager by inject(DockerManager::class.java)
        val template: TemplateManager by inject(TemplateManager::class.java)
        val serverManager: ServerManager by inject(ServerManager::class.java)


        redis.connect()
        docker.loadDockerClient()

        template.loadTemplatesFromFile()

        serverManager.startup()

        // add shutdown hook to close resources
        Runtime.getRuntime().addShutdownHook(Thread {
            serverManager.cleanup {
                redis.close()
            }
        })

        while (true) {
            // check input from console
            val input = readLine()
            if (input == null || input.lowercase() == "exit") {
                println("Exiting KloudInstance...")
                break
            } else {
                println("Received input: $input")
            }
        }
    }
}