package net.starlegacy.command.space

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.database.schema.space.Star
import net.starlegacy.util.green
import net.starlegacy.util.msg
import net.starlegacy.util.randomDouble
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("planet")
@CommandPermission("space.planet")
object PlanetCommand : SLCommand() {
    @Subcommand("create")
    @CommandCompletion("@nothing @stars @worlds 1000|2000|3000|4000|5000 1|2|3|4|5 0.5|0.75|1.0")
    fun onCreate(
        sender: CommandSender,
        name: String,
        sun: Star,
        planetWorldName: String,
        orbitDistance: Int, orbitSpeed: Double,
        size: Double
    ) {
        if (size <= 0 || size > 1) {
            throw InvalidCommandArgument("Size must be more than 0 and no more than 1")
        }

        if (PlanetCache.getByName(name) != null) {
            throw InvalidCommandArgument("A planet with that name already exists!")
        }

        val seed: Long = name.hashCode().toLong()
        val orbitProgress: Double = randomDouble(0.0, 360.0)

        Planet.create(name, sun._id, planetWorldName, size, orbitDistance, orbitSpeed, orbitProgress, seed)

        sender msg green("Created planet $name")
    }

    @Subcommand("set seed")
    @CommandCompletion("@planets 0")
    fun onSetSeed(sender: CommandSender, planet: Planet, newSeed: Long) {
        Planet.setSeed(planet._id, newSeed)

        sender msg green("Updated seed.")
    }

    @Subcommand("set atmosphere materials")
    @CommandCompletion("@planets @nothing")
    fun onSetAtmosphereMaterials(sender: CommandSender, planet: Planet, newMaterials: String) {
        val materials: List<String> = try {
            newMaterials.split(" ")
                .map { "${Material.valueOf(it)}" }
        } catch (exception: Exception) {
            exception.printStackTrace()
            throw InvalidCommandArgument("An error occurred parsing materials, try again")
        }

        Planet.setCloudMaterials(planet._id, materials)

        sender msg "Updated atmosphere materials."
    }

    @Subcommand("set atmosphere density")
    @CommandCompletion("@planets 0.1|0.2|0.3|0.4|0.5")
    fun onSetAtmosphereDensity(sender: CommandSender, planet: Planet, newDensity: Double) {
        Planet.setCloudDensity(planet._id, newDensity)

        sender msg green("Updated atmosphere density.")
    }

    @Subcommand("set atmosphere noise")
    @CommandCompletion("@planets 0.1|0.2|0.3|0.4|0.5")
    fun onSetAtmosphereNoise(sender: CommandSender, planet: Planet, newNoise: Double) {
        Planet.setCloudDensityNoise(planet._id, newNoise)

        sender msg green("Updated atmosphere noise.")
    }

    @Subcommand("set cloud threshold")
    @CommandCompletion("@planets 0.1|0.2|0.3|0.4|0.5")
    fun onSetCloudThreshold(sender: CommandSender, planet: Planet, newThreshold: Double) {
        Planet.setCloudThreshold(planet._id, newThreshold)

        sender msg green("Updated cloud density.")
    }

    @Subcommand("set cloud noise")
    @CommandCompletion("@planets 0.1|0.2|0.3|0.4|0.5")
    fun onSetCloudNoise(sender: CommandSender, planet: Planet, newNoise: Double) {
        Planet.setCloudNoise(planet._id, newNoise)

        sender msg green("Updated cloud noise.")
    }

    @Subcommand("set crust noise")
    @CommandCompletion("@planets 0.1|0.2|0.3|0.4|0.5")
    fun onSetCrustNoise(sender: CommandSender, planet: Planet, newNoise: Double) {
        Planet.setCrustNoise(planet._id, newNoise)

        sender msg green("Updated crust noise.")
    }

    @Subcommand("set crust materials")
    @CommandCompletion("@planets @nothing")
    fun onSetCrustMaterials(sender: CommandSender, planet: Planet, newMaterials: String) {
        val materials: List<String> = try {
            newMaterials.split(" ")
                .map { "${Material.valueOf(it)}" }
        } catch (exception: Exception) {
            exception.printStackTrace()
            throw InvalidCommandArgument("An error occurred parsing materials, try again")
        }

        Planet.setCrustMaterials(planet._id, materials)

        sender msg "Updated crust materials."
    }

    @Subcommand("set sun")
    @CommandCompletion("@planets @stars")
    fun onSetSun(sender: CommandSender, planet: Planet, newSun: Star) {
        Planet.setSun(planet._id, newSun._id)
        sender msg green("Updated sun to ${newSun.name} and updated database")
    }

    @Subcommand("set orbit distance")
    @CommandCompletion("@planets @nothing")
    fun onSetOrbitDistance(sender: CommandSender, planet: Planet, newDistance: Int) {
        val oldDistance = planet.orbitDistance
        Planet.setOrbitDistance(planet._id, newDistance)
        sender msg green("Updated distance from $oldDistance to $newDistance, moved the planet, and updated database")
    }

    @Subcommand("getpos")
    @CommandCompletion("@planets")
    fun onGetPos(sender: CommandSender, planet: Planet) {
        sender msg "&7${planet.name}&b is at &e${planet.instance.position}&b in &c${planet.instance.spaceWorldName}&b. " +
                "Its planet world is &2${planet.planetWorld}"
    }


    @Subcommand("info")
    @CommandCompletion("@planets")
    fun onInfo(sender: CommandSender, planet: Planet) {
        sender msg "&2${planet.name}"
        sender msg "  &7Sun:&b ${planet.instance.sun.name}"
        sender msg "  &7Space World:&b ${planet.instance.spaceWorldName}"
        sender msg "  &7Planet World:&b ${planet.planetWorld}"
        sender msg "  &7Size:&b ${planet.size}"
        sender msg "  &7Atmosphere Density:&b ${planet.cloudDensity}"
        sender msg "  &7Atmosphere Radius:&b ${planet.atmosphereRadius}"
        sender msg "  &7Atmosphere Materials:&b ${planet.cloudMaterials}"
        sender msg "  &7Crust Radius:&b ${planet.crustRadius}"
        sender msg "  &7Crust Materials:&b ${planet.crustMaterials}"
    }

    @Subcommand("teleport|tp")
    @CommandCompletion("@planets")
    fun onTeleport(sender: Player, planet: Planet) {
        val world = planet.instance.spaceWorld ?: throw ConditionFailedException("Planet's space world is not loaded!")

        val location = planet.instance.position.toLocation(world).apply {
            y = 255.0
        }

        sender.teleport(location)

        sender msg green("Teleported to ${planet.name}")
    }

    @Subcommand("delete")
    @CommandCompletion("@planets")
    fun onDelete(sender: CommandSender, planet: Planet) {
        Planet.delete(planet._id)
        sender msg "&aDeleted planet ${planet.name}"
    }
}
