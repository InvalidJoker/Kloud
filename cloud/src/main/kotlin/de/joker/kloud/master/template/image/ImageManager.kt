package de.joker.kloud.master.template.image

import de.joker.kloud.shared.utils.logger
import dev.fruxz.ascend.json.globalJson
import kotlinx.serialization.builtins.ListSerializer
import org.koin.core.component.KoinComponent
import java.io.File
import kotlin.collections.set
import kotlin.system.exitProcess

class ImageManager : KoinComponent {
    private val images = mutableMapOf<String, Image>()


    private fun addImage(image: Image) {
        images[image.name] = image
    }

    fun getImage(name: String): Image? {
        return images[name]
    }

    fun listImages(): List<Image> {
        return images.values.toList()
    }

    fun loadImagesFromFile() {
        val file = File("images.json")

        if (file.exists()) {
            val content = file.readText()
            val loadedImages = globalJson.decodeFromString<List<Image>>(content)

            // check if any image has same name
            if (loadedImages.groupBy { it.name }.any { it.value.size > 1 }) {
                logger.error("Duplicate image names found in images.json. Please ensure all image names are unique.")
                exitProcess(1)
            }

            loadedImages.forEach {
                addImage(it)
            }
        } else {
            val velocity = Image(
                name = "velocity",
                image = "itzg/mc-proxy",
                environment = mapOf(
                    "TYPE" to "VELOCITY",
                ),
                startedMessageRegex = "Done \\(.*\\)!"
            )
            val paper = Image(
                name = "paper",
                image = "itzg/minecraft-server",
                environment = mapOf(
                    "TYPE" to "PAPER",
                ),
                startedMessageRegex = "Done \\(.*\\)! For help, type \"help\"$"
            )
            addImage(velocity)
            addImage(paper)
            file.writeText(globalJson.encodeToString(ListSerializer(Image.serializer()), listOf(
                velocity,
                paper
            )))
            logger.warn("Images file not found: ${file.absolutePath}. Created default images: 'paper' and 'velocity'.")
        }
    }
}