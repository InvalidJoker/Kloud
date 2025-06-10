package de.joker.kloud.shared.server

import build.buf.gen.templates.v1.ServerType as ProtoServerType

enum class ServerType {
    PROXY,
    PROXIED_SERVER,
    STANDALONE_SERVER;

    fun toProto(): ProtoServerType {
        return when (this) {
            PROXY -> ProtoServerType.PROXY
            PROXIED_SERVER -> ProtoServerType.PROXIED_SERVER
            STANDALONE_SERVER -> ProtoServerType.STANDALONE_SERVER
        }
    }

    companion object {
        fun fromProto(proto: ProtoServerType): ServerType {
            return when (proto) {
                ProtoServerType.PROXY -> PROXY
                ProtoServerType.PROXIED_SERVER -> PROXIED_SERVER
                ProtoServerType.STANDALONE_SERVER -> STANDALONE_SERVER
                ProtoServerType.UNRECOGNIZED -> throw IllegalArgumentException("Unknown server type: $proto")
            }
        }
    }
}