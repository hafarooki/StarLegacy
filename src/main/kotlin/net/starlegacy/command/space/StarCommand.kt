package net.starlegacy.command.space

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.space.StarCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.space.Star
import net.starlegacy.feature.space.WorldFlags.Flag
import net.starlegacy.feature.space.WorldFlags.isFlagSet
import net.starlegacy.feature.space.celestialbody.StarClassification
import net.starlegacy.util.green
import net.starlegacy.util.msg
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("star")
@CommandPermission("space.star")
object StarCommand : SLCommand() {
    @Subcommand("create")
    @CommandCompletion("@nothing @worlds @nothing @nothing SEA_LANTERN|GLOWSTONE|MAGMA @nothing")
    fun onCreate(
        sender: CommandSender,
        name: String,
        spaceWorld: World,
        x: Int,
        z: Int,
        classification: StarClassification,
    ) {
        if (!isFlagSet(spaceWorld, Flag.SPACE)) {
            throw InvalidCommandArgument("Not a space world!")
        }

        if (StarCache.getByName(name) != null) {
            throw InvalidCommandArgument("A star with that name already exists!")
        }

        Star.create(name, spaceWorld.name, x, 128, z, classification)

        sender msg green("Created star $name of class $classification at $x $z in $spaceWorld")
    }


    @Subcommand("getpos")
    @CommandCompletion("@stars")
    fun onGetPos(sender: CommandSender, star: Star) {
        sender msg "&7${star.name}&b is at &e${star.instance.position}&b in &c${star.instance.spaceWorldName}&b."
    }

    @Subcommand("teleport|tp")
    @CommandCompletion("@stars")
    fun onTeleport(sender: Player, star: Star) {
        val world = star.instance.spaceWorld ?: throw ConditionFailedException("Star's space world is not loaded!")

        val location = star.instance.position.toLocation(world).apply {
            y = 255.0
        }

        sender.teleport(location)

        sender msg green("Teleported to ${star.name}")
    }

    @Subcommand("move")
    @CommandCompletion("@stars @nothing @nothing")
    fun onMove(sender: CommandSender, star: Star, spaceWorld: World, newX: Int, newZ: Int) {
        Star.setPos(star._id, spaceWorld.name, newX, 128, newZ)
        sender msg "Moved star ${star.name} to $newX, $newZ"
    }
}
