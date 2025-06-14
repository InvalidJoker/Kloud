package de.joker.kloud.proxy.command.args

import com.velocitypowered.api.proxy.Player
import de.joker.kloud.proxy.ProxyPlugin
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.TextArgument

open class PlayerArgument(nodeName: String) : CustomArgument<Player, String>(
    TextArgument(nodeName),
    { info ->
        val raw = info.currentInput

        val p = ProxyPlugin.instance.server.getPlayer(raw)

        if (p.isEmpty || !p.get().isActive) {
           throw CustomArgumentException.fromMessageBuilder(MessageBuilder()
                .append("Player not found: ")
                .appendArgInput()
           )
        }

        p.get()
    }
)

inline fun CommandAPICommand.playerArgument(
    nodeName: String,
    optional: Boolean = false,
    block: Argument<*>.() -> Unit = {},
): CommandAPICommand =
    withArguments(PlayerArgument(nodeName).setOptional(optional).apply(block))

inline fun CommandTree.playerArgument(
    nodeName: String,
    optional: Boolean = false,
    block: Argument<*>.() -> Unit = {},
): CommandTree =
    then(PlayerArgument(nodeName).setOptional(optional).apply(block))