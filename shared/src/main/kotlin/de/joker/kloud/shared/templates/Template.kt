package de.joker.kloud.shared.templates

import de.joker.kloud.shared.server.ServerType
import kotlinx.serialization.Serializable
import build.buf.gen.templates.v1.Template as ProtoTemplate

@Serializable
data class Template(
    val name: String,
    val image: String,
    val environment: Map<String, String>,
    val lobby: Boolean = false,
    val type: ServerType = ServerType.PROXIED_SERVER,
    val requiredPermissions: List<String> = emptyList(),
    val dynamic: DynamicTemplate? = null, // if set server is dynamic and can scale
) {
    fun toProto(): ProtoTemplate {
        return ProtoTemplate.newBuilder()
            .setName(name)
            .setImage(image)
            .putAllEnvironment(environment)
            .setLobby(lobby)
            .setType(type.toProto())
            .addAllRequiredPermissions(requiredPermissions)
            .apply {
                this@Template.dynamic?.let {
                    dynamic = it.toProto()
                }
            }
            .build()
    }

    companion object {
        fun fromProto(proto: ProtoTemplate): Template {
            return Template(
                name = proto.name,
                image = proto.image,
                environment = proto.environmentMap,
                lobby = proto.lobby,
                type = ServerType.fromProto(proto.type),
                requiredPermissions = proto.requiredPermissionsList,
                dynamic = if (proto.hasDynamic()) {
                    DynamicTemplate(
                        minServers = proto.dynamic.minServers,
                        maxServers = proto.dynamic.maxServers,
                        extraDirectories = proto.dynamic.extraDirectoriesList
                    )
                } else null
            )
        }
    }
}