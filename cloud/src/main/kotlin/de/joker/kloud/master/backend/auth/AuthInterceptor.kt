package de.joker.kloud.master.backend.auth

import io.grpc.*
import kotlinx.coroutines.runBlocking

class AuthInterceptor(
    private val token: String,
) : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val secretKey = headers.get(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER))
        if (secretKey == null || secretKey != token) {
            call.close(Status.UNAUTHENTICATED, headers)
            return object : ServerCall.Listener<ReqT>() {}
        }

        return runBlocking {
            next.startCall(call, headers)
        }
    }

}