package net.starlegacy.cache.nations

import com.googlecode.cqengine.index.compound.CompoundIndex
import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.QueryFactory.and
import com.googlecode.cqengine.query.QueryFactory.equal
import net.starlegacy.cache.Cache
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.feature.multiblock.defenseturret.APTurretMultiblock
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionNationOutpost
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.nations.region.types.RegionTerritory
import net.starlegacy.util.distance
import org.bukkit.Location
import org.bukkit.entity.Player

object RelationCache : DbObjectCache<NationRelation>(NationRelation.Companion), Cache {
    override val idAttribute = idAttribute(NationRelation::_id)
    private val nationAttr = propertyAttribute(NationRelation::nationId)
    private val otherAttr = propertyAttribute(NationRelation::otherId)

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nationAttr))
        cache.addIndex(HashIndex.onAttribute(otherAttr))
        cache.addIndex(CompoundIndex.onAttributes(nationAttr, otherAttr))
    }

    operator fun get(nationId: Oid<Nation>, otherId: Oid<Nation>): NationRelation.Level = when (nationId) {
        otherId -> NationRelation.Level.NATION
        else -> cache.retrieve(and(equal(nationAttr, nationId), equal(otherAttr, otherId)))?.firstOrNull()
            ?.actual ?: NationRelation.Level.NONE
    }

    fun isHostile(player: Player, location: Location): Boolean {
        val playerNation = PlayerCache[player].nationId

        for (region in Regions.find(location)) {
            if (APTurretMultiblock.regionalTargets[region.dbObjectId].contains(player.uniqueId)) {
                return true
            }

            if (playerNation == null) {
                continue
            }

            when (region) {
                is RegionSettlement -> {
                    val settlement = SettlementCache[region.dbObjectId]
                    val settlementNation = settlement.nationId ?: continue

                    return RelationCache[settlementNation, playerNation] <= NationRelation.Level.ENEMY
                }
                is RegionNationOutpost -> {
                    if (RelationCache[region.nationId, playerNation] <= NationRelation.Level.ENEMY) {
                        return true
                    }
                }
                is RegionTerritory -> {
                    val territory = TerritoryCache[region.dbObjectId]

                    val nearestBastion = territory.bastions.minByOrNull {
                        distance(it.x, it.y, it.z, location.blockX, location.blockY, location.blockZ)
                    } ?: continue

                    val nationId = nearestBastion.occupierId
                        ?: continue

                    if (RelationCache[nationId, playerNation] <= NationRelation.Level.ALLY) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
