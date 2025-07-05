package de.joker.kloud.master.core

import build.buf.gen.generic.v1.GenericResponse
import build.buf.gen.manager.v1.ManagerServiceGrpcKt
import build.buf.gen.manager.v1.StopCloudRequest
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.server.ServerManager
import de.joker.kloud.shared.events.CloudStoppedEvent
import de.joker.kloud.shared.redis.RedisNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class ManagerService: ManagerServiceGrpcKt.ManagerServiceCoroutineImplBase() {
    override suspend fun stopCloud(request: StopCloudRequest): GenericResponse {
        val serverManager: ServerManager by inject(ServerManager::class.java)
        val redis: RedisConnector by inject(RedisConnector::class.java)

        CoroutineScope(context).launch {
            redis.emit(RedisNames.CLOUD, CloudStoppedEvent())
            serverManager.cleanupCurrent()
            serverManager.shutdownJob.cancel()
        }

        return GenericResponse.newBuilder().build()
    }
}