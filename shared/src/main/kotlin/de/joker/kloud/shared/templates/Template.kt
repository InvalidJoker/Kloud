package de.joker.kloud.shared.templates

import de.joker.kloud.shared.server.ServerType
import kotlinx.serialization.Serializable
import build.buf.gen.templates.v1.Template as ProtoTemplate

/**
 * Represents a server template configuration.
 *
 * This template defines the behavior and properties of a Minecraft server,
 * including its environment, type, dynamic scaling, permissions, and more.
 *
 * @property name The unique name of the template.
 * @property image The Docker image used to start the server container.
 * @property environment A map of environment variables to be passed to the server container.
 * @property lobby Whether this server acts as a lobby (entry point or fallback server).
 * @property type The type of server (e.g., proxied or standalone).
 * @property requiredPermissions A list of permissions required to start or interact with this template.
 * @property priority The server priority. Higher values indicate higher importance.
 * @property dynamic If set, the server is dynamic and supports auto-scaling.
 * @property forcedPort Only for static servers. Forces the server to always use this port if set.
 */
@Serializable
data class Template(
    val name: String,
    val image: String,
    val environment: Map<String, String>,
    val lobby: Boolean = false,
    val type: ServerType = ServerType.PROXIED_SERVER,
    val requiredPermissions: List<String> = emptyList(),
    val priority: Int = 0,
    val dynamic: DynamicTemplate? = null,
    val forcedPort: Int? = null,
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