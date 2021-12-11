package net.starlegacy.feature.nations.region.types

import com.mongodb.client.model.changestream.ChangeStreamDocument
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.database.*
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.schema.nations.SettlementRole
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.util.chunkKey
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class RegionSettlement(settlement: Settlement) : Region<Settlement>(settlement),
    RegionTopLevel, RegionParent {
    override val priority: Int = 0

    var name: String = settlement.name; private set
    override var world: String = settlement.worldName; private set
    override val children: MutableSet<Region<*>> = ConcurrentHashMap.newKeySet()
    private var leader: SLPlayerId = settlement.leaderId
    private var nation: Oid<Nation>? = settlement.nationId
    private var chunks: LongOpenHashSet = LongOpenHashSet(settlement.chunks)
    private var minBuildAccess: Settlement.ForeignRelation = settlement.minimumBuildAccess

    override fun contains(x: Int, y: Int, z: Int): Boolean = chunks.contains(chunkKey(x shr 4, z shr 4))

    override fun update(delta: ChangeStreamDocument<Settlement>) {
        delta[Settlement::name]?.let { name = it.string() }
        delta[Settlement::worldName]?.let { world = it.string() }
        delta[Settlement::nationId]?.let { nation = it.nullable()?.oid() }
        delta[Settlement::chunks]?.let { bson ->
            chunks = LongOpenHashSet(bson.array().mappedSet { it.asInt64().value })
        }
        delta[Settlement::minimumBuildAccess]?.let { minBuildAccess = it.enumValue() }

        Regions.refreshSettlementMembersLocally(dbObjectId)
    }

    override fun onDelete() {
        Regions.refreshSettlementMembersLocally(dbObjectId)
    }

    override fun calculateInaccessMessage(player: Player): String? {
        val playerData = PlayerCache[player]
        val playerSettlement: Oid<Settlement>? = playerData.settlementId

        // anyone can build o_o
        if (minBuildAccess == Settlement.ForeignRelation.NONE) {
            return null
        }

        // other than that ^, building in a settlement requires being in a settlement
        if (playerSettlement != null) {
            // if they're a member of the settlement...
            if (playerSettlement == this.dbObjectId) {
                // if it's set to settlement members+, they can build
                if (minBuildAccess <= Settlement.ForeignRelation.SETTLEMENT_MEMBER) {
                    return null
                }

                if (leader == player.slPlayerId) {
                    return null
                }

                if (SettlementRole.hasPermission(player.slPlayerId, SettlementRole.Permission.BUILD)) {
                    return null
                }

                return "You don't have the BUILD permission and minbuildaccess is STRICT!"
            }

            val playerNation: Oid<Nation>? = playerData.nationId

            // if they're in a nation, and min build access is nation member or ally there's a chance they can build
            if (playerNation != null && this.minBuildAccess <= Settlement.ForeignRelation.NATION_MEMBER) {
                val ourNation = this.nation

                // if it's nation access, they can build if they're the same nation
                if (this.minBuildAccess == Settlement.ForeignRelation.NATION_MEMBER && ourNation == playerNation) {
                    return null
                }

                // if the min build access is ally and they're at least an ally, they can build
                if (this.minBuildAccess == Settlement.ForeignRelation.ALLY
                    && ourNation != null
                    && RelationCache[ourNation, playerNation] >= NationRelation.Level.ALLY
                ) {
                    return null
                }
            }
        }

        return "$name is claimed by the settlement ${this.name}"
    }

    override fun toString(): String = "RegionSettlement($name)"
}
