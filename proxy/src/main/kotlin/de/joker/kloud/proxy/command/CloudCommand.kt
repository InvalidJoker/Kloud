package de.joker.kloud.proxy.command

import de.joker.kloud.proxy.ProxyPlugin
import de.joker.kloud.shared.api.APIWrapper
import dev.fruxz.stacked.text
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.stringArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object CloudCommand: KoinComponent {
    fun register() {
        return commandTree("cloud") {
            literalArgument("servers") {
                literalArgument("start") {
                    stringArgument("group") {
                        playerExecutor { p, args ->
                            val group: String by args

                            val api by inject<APIWrapper>()

                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    val res = api.createServer(group)
                                    if (res.templateNotFound) {
                                        p.sendMessage(text("<red>Template for group '$group' not found."))
                                        return@launch
                                    } else if (res.maximumServersReached) {
                                        p.sendMessage(text("<red>Maximum servers reached for group '$group'."))
                                        return@launch
                                    } else if (res.id != null) {
                                        p.sendMessage(text("<green>Group '$group' started successfully with ID: ${res.id}"))
                                    } else {
                                        p.sendMessage(text("<red>Failed to start group '$group'."))
                                        return@launch
                                    }
                                } catch (e: Exception) {
                                    p.sendMessage(text("<red>Error starting group: ${e.message}"))
                                }
                            }
                        }
                    }
                }
                literalArgument("list") {
                    playerExecutor { p, _ ->
                        p.sendMessage(text("<white>Available servers: <yellow>${ProxyPlugin.instance.server.allServers.size}</yellow>"))

                        // Here you would add the logic to list players
                        ProxyPlugin.instance.server.allServers.forEach { server ->
                            val serverName = server.serverInfo.name
                            val players = server.playersConnected

                            val playerNames = if (players.isEmpty()) {
                                "No players online"
                            } else {
                                players.joinToString(", ") { it.username }
                            }

                            p.sendMessage(text("- $serverName > <yellow>$playerNames</yellow>"))
                        }
                    }
                }
            }
        }
    }
}