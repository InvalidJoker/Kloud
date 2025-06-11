package de.joker.kloud.shared.utils

import de.joker.kloud.shared.events.CloudStartedEvent
import de.joker.kloud.shared.events.CloudStoppedEvent
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val eventModule = SerializersModule {
    polymorphic(IEvent::class) {
        subclass(ServerUpdateStateEvent::class)
        subclass(CloudStartedEvent::class)
        subclass(CloudStoppedEvent::class)
    }
}

val eventJson = Json {
    serializersModule = eventModule
    classDiscriminator = "_type"
    ignoreUnknownKeys = true
}