package de.joker.kloud.shared.common

import kotlinx.serialization.Serializable

@Serializable
enum class PowerAction {
    START,
    STOP,
    RESTART
}