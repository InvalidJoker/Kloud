package de.joker.kloud.shared.events

import de.joker.kloud.shared.common.PowerAction
import kotlinx.serialization.Serializable

@Serializable
data class SendPowerActionEvent(
    val serverId: String,
    val action: PowerAction,
    override val eventName: String = "sendPowerActionEvent"
) : IEvent