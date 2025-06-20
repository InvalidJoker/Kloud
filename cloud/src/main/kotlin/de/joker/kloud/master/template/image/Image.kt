package de.joker.kloud.master.template.image

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val name: String,
    val image: String,
    val defaultInternalPort: Int = 25565,
    val defaultVersion: String = "latest",
    val startedMessageRegex: String = "^Done \\(\\d+\\.\\d+s\\)! For help, type \"help\"$",
    val environment: Map<String, String> = emptyMap(),
) {
    val startedMessageRegexPattern by lazy {
        Regex(startedMessageRegex)
    }
}