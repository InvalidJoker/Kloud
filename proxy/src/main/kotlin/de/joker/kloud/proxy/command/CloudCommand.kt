package de.joker.kloud.proxy.command

import de.joker.kloud.proxy.ProxyPlugin
import de.joker.kloud.proxy.redis.RedisSubscriber
import de.joker.kloud.shared.api.APIWrapper
import de.joker.kloud.shared.server.ServerType
import dev.fruxz.stacked.text
import dev.jorel.commandapi.arguments.ArgumentSuggestions
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
        val api by inject<APIWrapper>()
        val redis by inject<RedisSubscriber>()
        return commandTree("cloud") {
            withPermission("kloud.command.cloud")
            literalArgument("servers") {
                literalArgument("start") {
                    withPermission("kloud.command.cloud.start")
                    stringArgument("group") {
                        includeSuggestions(
                            ArgumentSuggestions.strings {
                                val templates = api.getTemplatesSync()

                                templates.map { it.name }.toTypedArray()
                            }
                        )
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
                literalArgument("info") {
                    stringArgument("serverName") {
                        includeSuggestions(
                            ArgumentSuggestions.strings {
                                val servers = redis.getAllServers().filter { it.template.type == ServerType.PROXIED_SERVER }
                                servers.map { it.serverName }.toTypedArray()
                            }
                        )
                        playerExecutor { p, args ->
                            val serverName: String by args

                            val server = ProxyPlugin.instance.server.getServer(serverName)
                            if (server != null && server.isPresent) {
                                val server = server.get()
                                val players = server.playersConnected
                                val playerNames = if (players.isEmpty()) {
                                    "No players online"
                                } else {
                                    players.joinToString(", ") { it.username }
                                }
                                val info = redis.getServerByName(serverName)

                                p.sendMessage(text("<white>Server: <yellow>$serverName</yellow>"))
                                p.sendMessage(text("<white>Players: <yellow>$playerNames</yellow>"))
                                p.sendMessage(text("<white>Internal ID: <yellow>${info?.internalId ?: "N/A"}</yellow>"))
                                p.sendMessage(text("<white>Container ID: <yellow>${info?.containerId ?: "N/A"}</yellow>"))
                                p.sendMessage(text("<white>Template: <yellow>${info?.template?.name ?: "N/A"}</yellow>"))
                            } else {
                                p.sendMessage(text("<red>Server '$serverName' not found."))
                            }
                        }
                    }
                }
            }
        }
    }
}