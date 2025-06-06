package de.joker.kloud.master.data

import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val name: String,
    val image: String,
    val environment: Map<String, String>,
    val lobby: Boolean = false,
    val requiredPermissions: List<String> = emptyList(),
    val dynamic: DynamicTemplate? = null, // if set server is dynamic and can scale
) {
    fun getFilePaths(): List<String> {
        val base = "/templates"
        val mainPath = "$base/$name"
        return listOf(mainPath) + (dynamic?.extraDirectories?.map { dir ->
            "$mainPath/$dir"
        } ?: emptyList())
    }
}

@Serializable
data class DynamicTemplate(
    val minServers: Int,
    val maxServers: Int,
    val extraDirectories: List<String> = emptyList(),
)