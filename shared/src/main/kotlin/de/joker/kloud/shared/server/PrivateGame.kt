package de.joker.kloud.shared.server

import dev.fruxz.ascend.json.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class PrivateGame(
    @Serializable(with = UUIDSerializer::class)
    val host: UUID,
)