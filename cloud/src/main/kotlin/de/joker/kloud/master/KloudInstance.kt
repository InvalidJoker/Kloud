package de.joker.kloud.master

import de.joker.kloud.master.docker.DockerManager
import de.joker.kloud.master.redis.RedisManager
import de.joker.kloud.shared.events.CreateServerEvent
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.eventJson
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.logger.slf4jLogger
import kotlin.getValue

object KloudInstance {

    val dockerModule = module {
        single { DockerManager() }
    }

    val redisModule = module {
        single { RedisManager() }
    }

    @OptIn(InternalSerializationApi::class)
    fun start() {
        startKoin {
            slf4jLogger()
            modules(
                dockerModule,
                redisModule,
                module {
                    single { json }
                }
            )
        }

        // Initialize Docker client and pull images
        val redis: RedisManager by inject(RedisManager::class.java)
        val docker: DockerManager by inject(DockerManager::class.java)

        redis.connect()
        docker.loadDockerClient()

        // temp keep alive
        while (true) {
            val event = CreateServerEvent(
                template = "d"
            )
            redis.jedisPool.resource.use { jedis ->
                jedis.publish("servers", eventJson.encodeToString(IEvent::class.serializer(), event))
            }
            Thread.sleep(1000) // Sleep for 1 second before next iteration
        }
    }
}