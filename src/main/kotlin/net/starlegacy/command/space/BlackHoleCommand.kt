package net.starlegacy.command.space

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.space.BlackHoleCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.space.BlackHole
import net.starlegacy.feature.space.WorldFlags
import net.starlegacy.util.green
import net.starlegacy.util.msg
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("blackhole")
@CommandPermission("space.blackhole")
object BlackHoleCommand : SLCommand() {
    @Subcommand("create")
    @CommandCompletion("@nothing @worlds @nothing @nothing SEA_LANTERN|GLOWSTONE|MAGMA @nothing")
    fun onCreate(
        sender: CommandSender,
        name: String,
        spaceWorld: World,
        x: Int,
        z: Int,
        radius: Int,
    ) {
        if (!WorldFlags.isFlagSet(spaceWorld, WorldFlags.Flag.SPACE)) {
            throw InvalidCommandArgument("Not a space world!")
        }

        if (BlackHoleCache.getByName(name) != null) {
            throw InvalidCommandArgument("A blackHole with that name already exists!")
        }

        BlackHole.create(name, spaceWorld.name, x, 128, z, radius)

        sender msg green("Created blackHole $name with radius $radius at $x $z in $spaceWorld")
    }


    @Subcommand("getpos")
    @CommandCompletion("@blackHoles")
    fun onGetPos(sender: CommandSender, blackHole: BlackHole) {
        sender msg "&7${blackHole.name}&b is at &e${blackHole.instance.position}&b in &c${blackHole.instance.spaceWorldName}&b."
    }

    @Subcommand("teleport|tp")
    @CommandCompletion("@blackHoles")
    fun onTeleport(sender: Player, blackHole: BlackHole) {
        val world = blackHole.instance.spaceWorld ?: throw ConditionFailedException("BlackHole's space world is not loaded!")

        val location = blackHole.instance.position.toLocation(world).apply {
            y = 255.0
        }

        sender.teleport(location)

        sender msg green("Teleported to ${blackHole.name}")
    }

    @Subcommand("move")
    @CommandCompletion("@blackHoles @nothing @nothing")
    fun onMove(sender: CommandSender, blackHole: BlackHole, spaceWorld: World, newX: Int, newZ: Int) {
        BlackHole.setPos(blackHole._id, spaceWorld.name, newX, 128, newZ)
        sender msg "Moved blackHole ${blackHole.name} to $newX, $newZ"
    }
}
