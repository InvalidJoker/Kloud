package de.joker.kloud.proxy.config

import dev.fruxz.ascend.json.globalJson
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ConfigManager {
    lateinit var config: PluginConfig

    fun loadConfig(dataDirectory: Path) {
        val path = dataDirectory.resolve("config.json")

        if (path.exists()) {
            val default = PluginConfig(
                startNotificationEnabled = true,
                stopNotificationEnabled = true,
                startMessage = "<gray>[<green>+<gray>] <white>{serverName}",
                stopMessage = "<gray>[<red>-<gray>] <white>{serverName}",
                cloudCommandEnabled = true
            )
            path.writeText(globalJson.encodeToString(default))
            config = default
        } else {
            config = globalJson.decodeFromString(
                PluginConfig.serializer(),
                path.readText()
            )
        }
    }
}