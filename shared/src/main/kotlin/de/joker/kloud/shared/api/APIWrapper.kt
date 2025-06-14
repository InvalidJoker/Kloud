package de.joker.kloud.shared.api

import build.buf.gen.generic.v1.GenericRequest
import build.buf.gen.server.v1.CreateServerRequest
import build.buf.gen.server.v1.PrivateGameData
import build.buf.gen.server.v1.ServerServiceGrpcKt
import build.buf.gen.templates.v1.TemplateServiceGrpcKt
import com.github.benmanes.caffeine.cache.Caffeine
import de.joker.kloud.shared.templates.Template
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import java.util.concurrent.TimeUnit

class APIWrapper(
    private val host: String,
    private val port: Int,
    token: String,
) {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()

    private val templateCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, List<Template>>()

    private val serverCaller =
        ServerServiceGrpcKt.ServerServiceCoroutineStub(channel).withCallCredentials(
            AuthCallCredentials(token)
        )

    private val templateCaller =
        TemplateServiceGrpcKt.TemplateServiceCoroutineStub(channel).withCallCredentials(
            AuthCallCredentials(token)
        )

    fun getTemplatesSync(): List<Template> = runBlocking {
        return@runBlocking getTemplates(forceRefresh = false)
    }

    suspend fun getTemplates(
        forceRefresh: Boolean = false
    ): List<Template> {
        val cacheKey = "templates"
        if (!forceRefresh) {
            val cached = templateCache.getIfPresent(cacheKey)
            if (cached != null) {
                return cached
            }
        }

        val templates = templateCaller.listTemplates(GenericRequest.getDefaultInstance())
        val result = templates.templatesList.map { Template.fromProto(it) }

        templateCache.put(cacheKey, result)

        return result
    }

    data class ServerCreationResult(
        val id: String?,
        val maximumServersReached: Boolean,
        val templateNotFound: Boolean
    )

    suspend fun createServer(
        templateName: String,
        privateGameHost: UUID? = null,
        extraData: Map<String, String> = emptyMap()
    ): ServerCreationResult {
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

        var id: String

        try {
            val response = serverCaller.createServer(request.build())

            id = response.id ?: throw IllegalStateException("Server creation response did not contain an ID.")
        } catch (e: Exception) {
            val status = Status.fromThrowable(e)

            return when (status.code) {
                status.code -> {
                    ServerCreationResult(
                        id = null,
                        maximumServersReached = true,
                        templateNotFound = false
                    )
                }
                status.code -> {
                    ServerCreationResult(
                        id = null,
                        maximumServersReached = false,
                        templateNotFound = true
                    )
                }
                else -> {
                    throw e
                }
            }
        }
        return ServerCreationResult(
            id = id,
            maximumServersReached = false,
            templateNotFound = false
        )
    }
}