package net.starlegacy.command.misc

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.command.SLCommand
import net.starlegacy.feature.space.WorldFlags
import net.starlegacy.feature.space.WorldFlags.Flag
import net.starlegacy.feature.space.WorldFlags.isFlagSet
import net.starlegacy.util.green
import net.starlegacy.util.msg
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender

@CommandAlias("worldflag")
@CommandPermission("starlegacy.worldflag")
object WorldFlagCommand : SLCommand() {
    @Subcommand("set")
    @CommandCompletion("@worlds @worldFlags true|false")
    fun onSet(sender: CommandSender, world: World, flag: Flag, value: Boolean) {
        WorldFlags.setFlag(world, flag, value)
        sender msg green("Set $flag for ${world.name} to space world: $value")
        onList(sender, flag, value)
    }

    @Subcommand("list")
    @CommandCompletion("@worldFlags true|false")
    fun onList(sender: CommandSender, flag: Flag, value: Boolean) {
        sender msg "&bWorlds matching $flag = $value: &d" + (Bukkit.getWorlds()
            .filter { isFlagSet(it, flag) == value }
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: "None")
    }
}
