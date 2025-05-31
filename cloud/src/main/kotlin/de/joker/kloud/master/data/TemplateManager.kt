package de.joker.kloud.master.data

import de.joker.kloud.master.json
import de.joker.kloud.master.logger
import org.koin.core.component.KoinComponent
import java.io.File

class TemplateManager : KoinComponent{
    private val templates = mutableMapOf<String, Template>()

    fun addTemplate(template: Template) {
        templates[template.name] = template
    }

    fun getTemplate(name: String): Template? {
        return templates[name]
    }

    fun removeTemplate(name: String) {
        templates.remove(name)
    }

    fun listTemplates(): List<Template> {
        return templates.values.toList()
    }

    fun loadTemplatesFromFile() {
        val file = File("templates.json")

        if (file.exists()) {
            val content = file.readText()
            val loadedTemplates = json.decodeFromString<List<Template>>(content)
            loadedTemplates.forEach { addTemplate(it) }
        } else {
            val defaultTemplate = Template(
                name = "default",
                image = "itzg/minecraft-server",
                environment = mapOf(
                    "TYPE" to "PAPER",
                ),
                lobby = true,
                requiredPermissions = emptyList(),
                dynamic = null
            )
            addTemplate(defaultTemplate)
            logger.warn("Templates file not found: ${file.absolutePath}. No templates loaded.")
        }

        // create template directories if they do not exist
        templates.forEach { (name, template) ->
            val dir = File("templates/$name")
            if (!dir.exists()) {
                dir.mkdirs()
                logger.info("Created directory for template: $name at ${dir.absolutePath}")
            }
        }
    }
}