package de.joker.kloud.shared.api

import io.grpc.CallCredentials
import io.grpc.Metadata
import java.util.concurrent.Executor

class AuthCallCredentials(
    private val secretKey: String
) : CallCredentials() {

    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        appExecutor: Executor,
        applier: MetadataApplier
    ) {
        appExecutor.execute {
            val headers = Metadata()
            headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), secretKey)
            applier.apply(headers)
        }
    }

}