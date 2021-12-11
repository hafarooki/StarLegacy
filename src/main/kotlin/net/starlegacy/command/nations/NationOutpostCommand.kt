package net.starlegacy.command.nations

import co.aikar.commands.annotation.*
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.starlegacy.cache.nations.NationOutpostCache
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationOutpost
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.database.schema.space.Star
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.uuid
import net.starlegacy.feature.nations.NATIONS_BALANCE
import net.starlegacy.feature.space.WorldFlags
import net.starlegacy.util.*
import org.bukkit.World
import org.bukkit.entity.Player
import org.litote.kmongo.addToSet
import org.litote.kmongo.eq
import org.litote.kmongo.pull
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt

@CommandAlias("nation|n")
object NationOutpostCommand : SLCommand() {
    private fun validateName(name: String, outpostId: Oid<NationOutpost>?) {
        validateNameString(name)

        val all = NationOutpostCache.getAll()
        failIf(all.any { it.name.equals(name, ignoreCase = true) && it._id != outpostId }) {
            "an outpost named $name already exists"
        }
    }

    private fun checkDimensions(world: World, x: Int, z: Int, radius: Int, id: Oid<NationOutpost>?) {
        failIf(radius !in 15..10_000) { "Radius must be at least 15 and at most 10,000 blocks" }

        checkPlanetOrbitConflicts(world, radius, x, z)
        checkStarConflicts(world, x, z)
        checkNationOutpostConflicts(id, radius, x, z)
        checkCaptureConflicts(world, radius, x, z)
        checkSettlementConflicts(world, x, z, radius)
        checkWorldGuardConflicts(world, x, z, radius)
    }

    private fun checkWorldGuardConflicts(world: World, x: Int, z: Int, radius: Int) {
        val regionManager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            ?: return

        val ellipse = circleToEllipse2D(x, z, radius)

        for ((regionName, region) in regionManager.regions.entries) {
            val minX = region.minimumPoint.blockX
            val minZ = region.minimumPoint.blockZ
            val width = region.maximumPoint.blockX - minX + 1
            val length = region.maximumPoint.blockZ - minZ + 1
            val rectangle = Rectangle2D.Double(minX.toDouble(), minZ.toDouble(), width.toDouble(), length.toDouble())

            failIf(ellipse.intersects(rectangle)) {
                "This claim would intersect with the region $regionName"
            }
        }
    }

    private fun checkSettlementConflicts(world: World, x: Int, z: Int, radius: Int) {
        for (settlement in SettlementCache.listByWorld(world.name)) {
            val rectangles = settlement.chunks.map { chunkKey ->
                return@map chunkToRectangle2D(chunkKeyX(chunkKey), chunkKeyZ(chunkKey))
            }

            val circle = circleToEllipse2D(x, z, radius)
            failIf(rectangles.any { circle.intersects(it) }) { "This claim would be too close to the settlement ${settlement.name}" }
        }
    }

    private fun checkCaptureConflicts(world: World, radius: Int, x: Int, z: Int) {
        for (capture in TerritoryCache.getAll().filter { it.worldName == world.name }) {
            val minDistance = capture.radius + radius
            val distance = distance(x, 0, z, capture.x, 0, capture.z)

            failIf(distance <= minDistance) {
                "This claim would be too close to the capture ${capture.name}"
            }
        }
    }

    private fun checkNationOutpostConflicts(id: Oid<NationOutpost>?, radius: Int, x: Int, z: Int) {
        for (other in NationOutpost.all()) {
            if (other._id == id) {
                continue
            }

            val minDistance = other.radius + radius
            val distance = distance(x, 0, z, other.centerX, 0, other.centerZ)

            failIf(distance <= minDistance) {
                "This claim would be too close to the outpost ${other.name}"
            }
        }
    }

    private fun checkStarConflicts(world: World, x: Int, z: Int) {
        for (star: Star in StarCache.getAll().filter { it.instance.spaceWorld == world }) {
            val minDistance = 256
            val distance = distance(x, 0, z, star.x, 0, star.z)

            failIf(distance < minDistance) {
                "This claim would be too close to the star ${star.name}"
            }
        }
    }

    private fun checkPlanetOrbitConflicts(world: World, radius: Int, x: Int, z: Int) {
        for (planet: Planet in PlanetCache.getAll().filter { it.instance.spaceWorld == world }) {
            val padding = 1000
            val minDistance = planet.orbitDistance - padding - radius
            val maxDistance = planet.orbitDistance + padding + radius
            val distance = distance(x, 0, z, planet.instance.sun.x, 0, planet.instance.sun.z).toInt()

            failIf(distance in minDistance..maxDistance) {
                "This claim would be in the way of ${planet.name}'s orbit"
            }
        }
    }

    private fun calculateCost(oldRadius: Int, newRadius: Int): Int {
        /*  A_1 = pi * r^2
            A_2 = pi * r_f^2
            dA = A_2-A_1
            dA = pi * r_f^2 - pi * r^2
            dA = pi * (r_f^2 - r^2) */
        val deltaArea = Math.PI * (newRadius.squared() - oldRadius.squared())
        return (deltaArea * NATIONS_BALANCE.nation.costPerOutpostBlock).roundToInt()
    }

    @Subcommand("outpost create")
    @Description("Claim this area as an outpost")
    fun onCreate(sender: Player, name: String, radius: Int, @Optional cost: Int?) = asyncCommand(sender) {
        failIf(!sender.hasPermission("nations.nationoutpost.create")) {
            "You can't create outposts here!"
        }

        val nation: Nation = requireNationIn(sender)
        requireNationLeader(sender, nation)

        validateName(name, outpostId = null)

        val location = sender.location
        val world = location.world
        val x = location.blockX
        val z = location.blockZ
        checkDimensions(world, x, z, radius, null)

        failIf(WorldFlags.isFlagSet(world, WorldFlags.Flag.CIVILIZED)) {
            "You can only create an outpost in uncivilized worlds"
        }

        val realCost = calculateCost(0, radius)
        requireMoney(sender, realCost, "create an outpost")

        failIf(cost != realCost) {
            "You must acknowledge the cost of creating an outpost to create one. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/noutpost $name $radius $realCost"
        }

        NationOutpost.create(nation._id, name, world.name, x, z, radius)
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())
        Notify.nation(nation._id, "&d${sender.name} &7established outpost &b$name")
    }

    private fun requireOutpost(nation: Nation, name: String) = NationOutpost.find(NationOutpost::nationId eq nation._id)
        .firstOrNull { it.name.equals(name, true) }
        ?: fail { "Your nation doesn't own an outpost named $name" }

    private fun requireManagementContext(sender: Player, name: String): Pair<Nation, NationOutpost> {
        val nation: Nation = requireNationIn(sender)
        val nationOutpost: NationOutpost = requireOutpost(nation, name)
        if (!nationOutpost.managerIds.contains(sender.slPlayerId)) {
            requireNationLeader(sender, nation)
        }
        return nation to nationOutpost
    }

    @Subcommand("outpost abandon")
    @Description("Delete an outpost")
    fun onAbandon(sender: Player, outpostName: String) = asyncCommand(sender) {
        val (nation, outpost) = requireManagementContext(sender, outpostName)
        requireNationLeader(sender, nation) // also require that they're the leader
        NationOutpost.delete(outpost._id)
        Notify.all("&d${nation.name} &7abandoned outpost &b$outpost")
    }

    @Subcommand("outpost resize")
    @Description("Resize the outpost")
    fun onResize(sender: Player, outpostName: String, newRadius: Int, @Optional cost: Int?) {
        val (nation, outpost) = requireManagementContext(sender, outpostName)
        requireNationLeader(sender, nation) // also require that they're the leader

        val location = sender.location
        val world = location.world
        val x = location.blockX
        val z = location.blockZ
        checkDimensions(world, x, z, newRadius, outpost._id)

        val realCost = calculateCost(outpost.radius, newRadius)
        requireMoney(sender, realCost, "create an outpost")

        failIf(cost != realCost) {
            "You must acknowledge the cost of resizing an outpost to resize one. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/noutpost resize $name $newRadius $realCost"
        }

        NationOutpost.updateById(outpost._id, org.litote.kmongo.setValue(NationOutpost::radius, newRadius))
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())
        sender msg "&7Resized &b${outpost.name}&7 to &b$newRadius"
    }

    @Subcommand("outpost set name")
    fun onSetName(sender: Player, outpostName: String, newName: String) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        validateName(name, outpost._id)
        NationOutpost.updateById(outpost._id, org.litote.kmongo.setValue(NationOutpost::name, newName))
        sender msg "&7Renamed &b$outpostName&7 to &b$newName"
    }

    @Subcommand("outpost set trustlevel")
    @CommandCompletion("MANUAL|NATION|ALLY")
    @Description("Change the setting for who automatically can build in the outpost")
    fun onSetTrustLevel(sender: Player, outpostName: String, trustLevel: NationOutpost.TrustLevel) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        failIf(outpost.trustLevel == trustLevel) { "${outpost.name}'s trust level is already $trustLevel" }
        NationOutpost.updateById(outpost._id, org.litote.kmongo.setValue(NationOutpost::trustLevel, trustLevel))
        sender msg "&7Set trust level of &b${outpost.name}&7 to &b$trustLevel"
    }

    @Subcommand("outpost manager add")
    fun onManagerAdd(sender: Player, outpostName: String, player: String) {
        val (nation, outpost) = requireManagementContext(sender, outpostName)
        requireNationLeader(sender, nation) // also require that they're the leader
        val playerId: SLPlayerId = resolveOfflinePlayer(player).slPlayerId
        val playerName: String = getPlayerName(playerId)

        failIf(outpost.managerIds.contains(playerId)) {
            "$playerName is already a manager of ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, addToSet(NationOutpost::managerIds, playerId))
        sender msg "&7Made &b$playerName&7 a manager of &b${outpost.name}"
        Notify.player(playerId.uuid, "&7You were made a manager of outpost &b${outpost.name}&7 by &b${sender.name}")
    }

    @Subcommand("outpost manager list")
    fun onManagerList(sender: Player, outpostName: String) {
        val (nation, outpost) = requireManagementContext(sender, outpostName)
        requireNationLeader(sender, nation) // also require that they're the leader
        val managers: String = outpost.managerIds.map(::getPlayerName).sorted().joinToString()
        sender msg "&7Managers in ${outpost.name}:&c $managers"
    }

    @Subcommand("outpost manager remove")
    @Description("Revoke a player's manager status at the outpost")
    fun onManagerRemove(sender: Player, outpostName: String, player: String) = asyncCommand(sender) {
        val (nation, outpost) = requireManagementContext(sender, outpostName)
        requireNationLeader(sender, nation) // also require that they're the leader
        val playerId: SLPlayerId = resolveOfflinePlayer(player).slPlayerId
        val playerName: String = getPlayerName(playerId)

        failIf(!outpost.managerIds.contains(playerId)) {
            "$playerName is not a manager of ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, pull(NationOutpost::managerIds, playerId))
        sender msg "&7Removed &b$playerName&7 as a manager of &b${outpost.name}"
        Notify.player(playerId.uuid, "&7You were removed as a manager of &b${outpost.name}&7 by &b${sender.name}")
    }

    @Subcommand("outpost trusted list")
    fun onTrustedList(sender: Player, outpostName: String) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        val trustedPlayers: String = outpost.trustedPlayerIds.map(::getPlayerName).sorted().joinToString()
        sender msg "&7Trusted players in ${outpost.name}:&b $trustedPlayers"
        val trustedNations: String = outpost.trustedNationIds.map(::getNationName).sorted().joinToString()
        sender msg "&7Trusted nations in ${outpost.name}:&b $trustedNations"
    }

    @Subcommand("outpost trusted add player")
    @Description("Give a player build access to the outpost")
    fun onTrustedAddPlayer(sender: Player, outpostName: String, player: String) = asyncCommand(sender) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        val playerId: SLPlayerId = resolveOfflinePlayer(player).slPlayerId
        val playerName: String = getPlayerName(playerId)

        failIf(outpost.trustedPlayerIds.contains(playerId)) {
            "$playerName is already trusted in ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, addToSet(NationOutpost::trustedPlayerIds, playerId))
        sender msg "&7Added &b$playerName&7 to &b${outpost.name}"
        Notify.player(playerId.uuid, "&7You were added to outpost &b${outpost.name}&7 by &b${sender.name}")
    }

    @Subcommand("outpost trusted remove player")
    @Description("Revoke a player's build access to the outpost")
    fun onTrustedRemovePlayer(sender: Player, outpostName: String, player: String) = asyncCommand(sender) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        val playerId: SLPlayerId = resolveOfflinePlayer(player).slPlayerId
        val playerName: String = getPlayerName(playerId)

        failIf(!outpost.trustedPlayerIds.contains(playerId)) {
            "$playerName is not trusted in ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, pull(NationOutpost::trustedPlayerIds, playerId))
        sender msg "&7Removed &b$playerName&7 from &b${outpost.name}"
        Notify.player(playerId.uuid, "&7You were removed from outpost &b${outpost.name}&7 by &b${sender.name}")
    }

    @Subcommand("outpost trusted add nation")
    @Description("Give a nation build access to the outpost")
    fun onTrustedAddNation(sender: Player, outpostName: String, nationName: String) = asyncCommand(sender) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        val nation: Nation = resolveNation(nationName)

        failIf(outpost.trustedNationIds.contains(nation._id)) {
            "${nation.name} is already a trusted nation in ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, addToSet(NationOutpost::trustedNationIds, nation._id))
        sender msg "&7Added nation &b${nation.name}&7 to &b${outpost.name}"
        Notify.nation(nation._id, "&7Your nation was added to outpost &b${outpost.name}&7 by &b${sender.name}")
    }

    @Subcommand("outpost trusted remove nation")
    @Description("Revoke a nation's build access to the outpost")
    fun onTrustedRemoveNation(sender: Player, outpostName: String, nationName: String) = asyncCommand(sender) {
        val (_, outpost) = requireManagementContext(sender, outpostName)
        val nation: Nation = resolveNation(nationName)

        failIf(!outpost.trustedNationIds.contains(nation._id)) {
            "$nationName is not a trusted nation in ${outpost.name}"
        }

        NationOutpost.updateById(outpost._id, pull(NationOutpost::trustedNationIds, nation._id))
        sender msg "&7Removed nation &b$nationName&7 from &b${outpost.name}"
        Notify.nation(nation._id, "&7Your nation was removed from outpost &b${outpost.name}&7 by &b${sender.name}")
    }
}
