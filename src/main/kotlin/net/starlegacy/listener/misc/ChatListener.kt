package net.starlegacy.listener.misc

import net.starlegacy.feature.chat.ChannelSelections
import net.starlegacy.feature.chat.ChatChannel
import net.starlegacy.listener.SLEventListener
import net.starlegacy.util.*
import org.bukkit.event.EventPriority
import org.bukkit.event.player.AsyncPlayerChatEvent

object ChatListener : SLEventListener() {
    override fun supportsVanilla(): Boolean {
        return true
    }

    override fun onRegister() {
        subscribe<AsyncPlayerChatEvent>(EventPriority.LOWEST).handler { event ->
            if (isInBedWarsGame(event.player)) {
                return@handler
            }

            val prefix = vaultChat.getPlayerPrefix(event.player)
            val suffix = vaultChat.getPlayerSuffix(event.player)
            event.format = "$prefix%s$suffix ${SLTextStyle.DARK_GRAY}» ${SLTextStyle.RESET}%s".colorize()

            if (!event.message.startsWith("!")) {
                val channel = ChannelSelections[event.player]
                event.message = "${channel.messageColor}${event.message}"
            }
        }

        subscribe<AsyncPlayerChatEvent>(EventPriority.HIGHEST)
            .filtered { !it.isCancelled }
            .handler { event ->
                if (isInBedWarsGame(event.player)) {
                    return@handler
                }

                event.isCancelled = true

                val channel = when {
                    event.message.startsWith("!") -> ChatChannel.GLOBAL
                    else -> ChannelSelections[event.player]
                }
                event.message = event.message.removePrefix("!").trim()
                if (event.message.isBlank()) {
                    return@handler
                }
                channel.onChat(event.player, event)
            }
    }
}
