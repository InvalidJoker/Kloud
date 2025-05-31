package de.joker.kloud.master

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json


@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    explicitNulls = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun main(args: Array<String>) {
    KloudInstance.start()
}