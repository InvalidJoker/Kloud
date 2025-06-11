package de.joker.kloud.master.backend.services

import build.buf.gen.generic.v1.GenericIdentification
import build.buf.gen.generic.v1.GenericResponse
import build.buf.gen.server.v1.*
import de.joker.kloud.master.core.ServerManager
import de.joker.kloud.master.template.TemplateManager
import de.joker.kloud.shared.server.PrivateGame
import de.joker.kloud.shared.server.ServerData
import io.grpc.Status
import io.grpc.StatusException
import org.koin.java.KoinJavaComponent.inject
import java.util.*

class ServerService : ServerServiceGrpcKt.ServerServiceCoroutineImplBase() {
    override suspend fun createServer(request: CreateServerRequest): ServerCreateResponse {

        val serverManager: ServerManager by inject(ServerManager::class.java)
        val templateManager: TemplateManager by inject(TemplateManager::class.java)
        val template = templateManager.getTemplate(request.templateId)

        if (template == null) {
            throw StatusException(Status.NOT_FOUND.withDescription("Template with ID '${request.templateId}' not found."))
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