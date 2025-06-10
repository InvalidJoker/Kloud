package de.joker.kloud.shared.server

import dev.fruxz.ascend.json.serializer.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class PrivateGame(
    @Serializable(with = UUIDSerializer::class)
    val host: UUID,
)