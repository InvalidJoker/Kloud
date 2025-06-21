package de.joker.kloud.master

import de.joker.kloud.master.template.image.ImageManager
import kotlin.test.Test
import kotlin.test.assertNotNull

class RegexTest {
    @Test
    fun testRegex() {
        val paperTest = "[21:16:26 INFO]: Done (19.342s)! For help, type \"help\""
        val velocityRegex = "[21:15:41 INFO]: Done (2.29s)!"

        val manager = ImageManager()

        manager.loadImagesFromFile()

        val paperRegex = manager.getImage("paper")?.startedMessageRegexPattern
            ?: throw IllegalStateException("Paper image not found")

        val velocityRegexPattern = manager.getImage("velocity")?.startedMessageRegexPattern
            ?: throw IllegalStateException("Velocity image not found")

        println("Paper Regex: $paperRegex")
        println("Velocity Regex: $velocityRegexPattern")

        val paperMatch = paperRegex.find(paperTest)
        val velocityMatch = velocityRegexPattern.find(velocityRegex)

        assertNotNull(paperMatch)
        assertNotNull(velocityMatch)
    }
}