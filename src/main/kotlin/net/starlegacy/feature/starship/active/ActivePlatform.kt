package net.starlegacy.feature.starship.active

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.starships.Platform
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionNationOutpost
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.nations.region.types.RegionTerritory
import net.starlegacy.feature.starship.StarshipType
import net.starlegacy.feature.starship.movement.StarshipMovement
import net.starlegacy.feature.starship.subsystem.weapon.WeaponSubsystem
import net.starlegacy.util.*
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class ActivePlatform(
    val data: Platform,
    blocks: LongOpenHashSet,
    mass: Double,
    centerOfMass: Vec3i,
    hitbox: ActiveStarshipHitbox,
) : ActiveStarship(data.bukkitWorld(), blocks, mass, centerOfMass, hitbox) {
    val id: Oid<Platform> = data._id

    override val weaponColor: Color
        get() = getNation()?.color?.let(Color::fromRGB) ?: Color.RED

    override fun moveAsync(movement: StarshipMovement): CompletableFuture<Boolean> {
        TODO("Not yet implemented")
    }

    override val type: StarshipType = StarshipType.DREADNOUGHT

    override val interdictionRange: Int = 0

    fun getNation(): Nation? {
        val location = centerOfMass.toLocation(world)

        val settlementRegion = Regions.findFirstOf<RegionSettlement>(location)
        if (settlementRegion != null) {
            val nationId = SettlementCache[settlementRegion.dbObjectId].nationId
                ?: return null
            return NationCache[nationId]
        }

        val nationOutpostRegion = Regions.findFirstOf<RegionNationOutpost>(location)
        if (nationOutpostRegion != null) {
            return NationCache[nationOutpostRegion.nationId]
        }

        val territoryRegion = Regions.findFirstOf<RegionTerritory>(location)
        if (territoryRegion != null) {
            val territory = TerritoryCache[territoryRegion.dbObjectId]
            val nearestBastion = territory.bastions.minByOrNull {
                distance(it.x, it.y, it.z, location.blockX, location.blockY, location.blockZ)
            } ?: return null
            val nationId = nearestBastion.occupierId
                ?: return null
            return NationCache[nationId]
        }

        return null
    }

    override fun getPotentialTargets(weapon: WeaponSubsystem): List<Player> {
        return world.players
            .filter { player -> player.gameMode == GameMode.SURVIVAL }
            .filter { player -> RelationCache.isHostile(player, centerOfMass.toLocation(world)) }
    }
}
