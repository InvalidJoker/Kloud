package de.joker.kloud.master.server

import build.buf.gen.generic.v1.GenericIdentification
import build.buf.gen.generic.v1.GenericResponse
import build.buf.gen.server.v1.CreateServerRequest
import build.buf.gen.server.v1.ServerCreateResponse
import build.buf.gen.server.v1.ServerServiceGrpcKt
import build.buf.gen.server.v1.UpdateServerRequest
import build.buf.gen.server.v1.privateGameDataOrNull
import de.joker.kloud.master.redis.RedisConnector
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.server.PrivateGame
import de.joker.kloud.shared.server.ServerData
import io.grpc.Status
import io.grpc.StatusException
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

class ServerService : ServerServiceGrpcKt.ServerServiceCoroutineImplBase() {
    override suspend fun createServer(request: CreateServerRequest): ServerCreateResponse {
        val serverManager: ServerManager by inject(ServerManager::class.java)
        val templateManager: TemplateManager by inject(TemplateManager::class.java)
        val template = templateManager.getTemplate(request.templateId)
        val redis: RedisConnector by inject(RedisConnector::class.java)

        if (template == null) {
            throw StatusException(Status.NOT_FOUND.withDescription("Template with ID '${request.templateId}' not found."))
        }

        if (template.dynamic == null && redis.getAllServers().any { it.template.name == template.name }) {
            throw StatusException(Status.ALREADY_EXISTS.withDescription("A static template '${template.name}' already has a server running."))
        }

        val privateGame = if (request.privateGameDataOrNull != null) {
            PrivateGame(
                UUID.fromString(request.privateGameData.hostUuid)
            )
        } else {
            null
        }

        val id = serverManager.createServer(
            template,
            ServerData(
                privateGame = privateGame,
                extraData = request.extraDataMap.toMap(),
            )
        )

        return ServerCreateResponse.newBuilder()
            .setId(id)
            .build()
    }

    override suspend fun updateServer(request: UpdateServerRequest): GenericResponse {
        val serverManager: ServerManager by inject(ServerManager::class.java)

        val privateGame = if (request.privateGameDataOrNull != null) {
            PrivateGame(
                UUID.fromString(request.privateGameData.hostUuid)
            )
        } else {
            null
        }

        val data = ServerData(
            privateGame = privateGame,
            extraData = request.extraDataMap.toMap(),
        )

        serverManager.updateServer(
            request.id,
            data
        )
        return GenericResponse.newBuilder().build()
    }

    override suspend fun restartServer(request: GenericIdentification): GenericResponse {
        val serverManager: ServerManager by inject(ServerManager::class.java)

        if (!serverManager.restartServer(request.id)) {
            throw StatusException(Status.NOT_FOUND.withDescription("Server with ID '${request.id}' not found."))
        }

        return GenericResponse.newBuilder().build()
    }

    override suspend fun stopServer(request: GenericIdentification): GenericResponse {
        val serverManager: ServerManager by inject(ServerManager::class.java)

        if (!serverManager.stopServer(request.id)) {
            throw StatusException(Status.NOT_FOUND.withDescription("Server with ID '${request.id}' not found."))
        }

        return GenericResponse.newBuilder().build()
    }
}