package de.joker.kloud.shared

import de.joker.kloud.shared.events.CreateServerEvent
import de.joker.kloud.shared.events.IEvent
import de.joker.kloud.shared.events.SendPowerActionEvent
import de.joker.kloud.shared.events.ServerUpdateStateEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val eventModule = SerializersModule {
    polymorphic(IEvent::class) {
        subclass(CreateServerEvent::class)
        subclass(ServerUpdateStateEvent::class)
        subclass(SendPowerActionEvent::class)
    }
}

val eventJson = Json {
    serializersModule = eventModule
    classDiscriminator = "_type"
    ignoreUnknownKeys = true
}