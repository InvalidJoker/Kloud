package de.joker.kloud.master.backend

import de.joker.kloud.master.Config
import de.joker.kloud.master.backend.auth.AuthInterceptor
import de.joker.kloud.master.server.ServerService
import de.joker.kloud.master.template.TemplatesService
import de.joker.kloud.shared.utils.logger
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class CloudBackend : KoinComponent {
    private fun createGrpcServer(): Server {
        return ServerBuilder.forPort(Config.backendPort)
            .addService(TemplatesService())
            .addService(ServerService())
            .intercept(AuthInterceptor(Config.apiToken))
            .build()
    }

    fun start() {
        val server = createGrpcServer()
        logger.info("Starting gRPC server...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server.start()
                server.awaitTermination()
            } catch (e: Exception) {
                logger.error("Failed to start gRPC server", e)
                throw e
            }
        }
    }
}