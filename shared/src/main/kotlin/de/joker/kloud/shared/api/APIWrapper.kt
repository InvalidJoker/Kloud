package de.joker.kloud.shared.api

import build.buf.gen.generic.v1.GenericRequest
import build.buf.gen.server.v1.CreateServerRequest
import build.buf.gen.server.v1.PrivateGameData
import build.buf.gen.server.v1.ServerServiceGrpcKt
import build.buf.gen.templates.v1.TemplateServiceGrpcKt
import de.joker.kloud.shared.templates.Template
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.UUID

class APIWrapper(
    private val host: String,
    private val port: Int,
    private val token: String,
) {
    private fun createControllerChannel(): ManagedChannel {
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    }
    private val serverCaller = ServerServiceGrpcKt.ServerServiceCoroutineStub(createControllerChannel()).withCallCredentials(
        AuthCallCredentials(token))

    private val templateCaller = TemplateServiceGrpcKt.TemplateServiceCoroutineStub(createControllerChannel()).withCallCredentials(
        AuthCallCredentials(token))

    suspend fun getTemplates(): List<Template> {
        val templates = templateCaller.listTemplates(GenericRequest.getDefaultInstance())

        return templates.templatesList.map { Template.fromProto(it) }
    }

    suspend fun createServer(
        templateName: String,
        privateGameHost: UUID? = null,
        extraData: Map<String, String> = emptyMap()
    ): String {
        val request = CreateServerRequest.newBuilder()
            .setTemplateId(templateName)

        if (privateGameHost != null) {
            request.privateGameData = PrivateGameData.newBuilder()
                .setHostUuid(privateGameHost.toString())
                .build()
        }

        if (extraData.isNotEmpty()) {
            request.extraDataMap.putAll(extraData)
        }

        val response = serverCaller.createServer(request.build())
        return response.id
    }
}