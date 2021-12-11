package net.starlegacy.command.space

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.space.CosmicBarrierCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.space.CosmicBarrier
import net.starlegacy.feature.space.WorldFlags.Flag
import net.starlegacy.feature.space.WorldFlags.isFlagSet
import net.starlegacy.util.green
import net.starlegacy.util.msg
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("cosmicbarrier")
@CommandPermission("space.cosmicbarrier")
object CosmicBarrierCommand : SLCommand() {
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
        if (!isFlagSet(spaceWorld, Flag.SPACE)) {
            throw InvalidCommandArgument("Not a space world!")
        }

        if (CosmicBarrierCache.getByName(name) != null) {
            throw InvalidCommandArgument("A cosmicBarrier with that name already exists!")
        }

        CosmicBarrier.create(name, spaceWorld.name, x, 128, z, radius)

        sender msg green("Created cosmicBarrier $name with radius $radius at $x $z in $spaceWorld")
    }


    @Subcommand("getpos")
    @CommandCompletion("@cosmicBarriers")
    fun onGetPos(sender: CommandSender, cosmicBarrier: CosmicBarrier) {
        sender msg "&7${cosmicBarrier.name}&b is at &e${cosmicBarrier.instance.position}&b in &c${cosmicBarrier.instance.spaceWorldName}&b."
    }

    @Subcommand("teleport|tp")
    @CommandCompletion("@cosmicBarriers")
    fun onTeleport(sender: Player, cosmicBarrier: CosmicBarrier) {
        val world = cosmicBarrier.instance.spaceWorld
            ?: throw ConditionFailedException("CosmicBarrier's space world is not loaded!")

        val location = cosmicBarrier.instance.position.toLocation(world).apply {
            y = 255.0
        }

        sender.teleport(location)

        sender msg green("Teleported to ${cosmicBarrier.name}")
    }

    @Subcommand("move")
    @CommandCompletion("@cosmicBarriers @nothing @nothing")
    fun onMove(sender: CommandSender, cosmicBarrier: CosmicBarrier, spaceWorld: World, newX: Int, newZ: Int) {
        CosmicBarrier.setPos(cosmicBarrier._id, spaceWorld.name, newX, 128, newZ)
        sender msg "Moved cosmicBarrier ${cosmicBarrier.name} to $newX, $newZ"
    }
}
