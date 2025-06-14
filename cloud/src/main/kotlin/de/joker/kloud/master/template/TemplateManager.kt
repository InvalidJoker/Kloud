package de.joker.kloud.master.template

import de.joker.kloud.shared.server.ServerType
import de.joker.kloud.shared.templates.DynamicTemplate
import de.joker.kloud.shared.templates.Template
import de.joker.kloud.shared.utils.logger
import dev.fruxz.ascend.json.globalJson
import kotlinx.serialization.builtins.ListSerializer
import org.koin.core.component.KoinComponent
import java.io.File
import kotlin.system.exitProcess


class TemplateManager : KoinComponent {
    private val templates = mutableMapOf<String, Template>()

    private fun addTemplate(template: Template) {
        templates[template.name] = template
    }

    fun getTemplate(name: String): Template? {
        return templates[name]
    }

    private fun removeTemplate(name: String) {
        templates.remove(name)
    }

    fun listTemplates(): List<Template> {
        return templates.values.toList()
    }

    fun loadTemplatesFromFile() {
        val file = File("templates.json")

        if (file.exists()) {
            val content = file.readText()
            val loadedTemplates = globalJson.decodeFromString<List<Template>>(content)
            if (loadedTemplates.any { it.type == ServerType.PROXY && it.lobby }) {
                logger.error("A proxy template cannot be a lobby. Please remove the lobby flag from the proxy template.")
                exitProcess(1)
            }

            loadedTemplates.forEach {
                if (templates.containsKey(it.name)) {
                    logger.warn("Template with name '${it.name}' already exists. Overwriting it.")
                }

                if (it.image.isBlank()) {
                    logger.error("Template '${it.name}' has an empty image field. Skipping this template.")
                    return@forEach
                }

                addTemplate(it)
            }
        } else {
            val proxy = Template(
                name = "proxy",
                image = "itzg/mc-proxy",
                environment = mapOf(
                    "TYPE" to "VELOCITY",
                ),
                type = ServerType.PROXY,
                lobby = false,
                requiredPermissions = emptyList(),
                dynamic = null
            )
            val lobby = Template(
                name = "lobby",
                image = "itzg/minecraft-server",
                environment = mapOf(
                    "TYPE" to "PAPER",
                ),
                type = ServerType.PROXIED_SERVER,
                lobby = true,
                requiredPermissions = emptyList(),
                dynamic = DynamicTemplate(
                    minServers = 1,
                    maxServers = 1
                )
            )
            addTemplate(proxy)
            addTemplate(lobby)
            file.writeText(globalJson.encodeToString(ListSerializer(Template.serializer()), listOf(proxy, lobby)))
            logger.warn("Templates file not found: ${file.absolutePath}. Created default templates: 'proxy' and 'lobby'.")
        }

        templates.forEach { (name, _) ->
            val dir = File("templates/$name")
            if (!dir.exists()) {
                dir.mkdirs()
                logger.info("Created directory for template: $name at ${dir.absolutePath}")
            }
        }
    }
}