package net.starlegacy.feature.nations.region.types

import com.mongodb.client.model.changestream.ChangeStreamDocument
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.database.Oid
import net.starlegacy.database.enumValue
import net.starlegacy.database.get
import net.starlegacy.database.int
import net.starlegacy.database.mappedSet
import net.starlegacy.database.oid
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.schema.nations.NationOutpost
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.string
import net.starlegacy.util.d
import net.starlegacy.util.distanceSquared
import net.starlegacy.util.squared
import org.bukkit.entity.Player

class RegionNationOutpost(nationOutpost: NationOutpost) : Region<NationOutpost>(nationOutpost), RegionTopLevel {
    override val priority: Int = 0

    override var world: String = nationOutpost.worldName; private set

    var name: String = nationOutpost.name; private set
    var x: Int = nationOutpost.centerX; private set
    var z: Int = nationOutpost.centerZ; private set
    var radius: Int = nationOutpost.radius; private set
    var nationId: Oid<Nation> = nationOutpost.nationId; private set
    var trustLevel: NationOutpost.TrustLevel = nationOutpost.trustLevel; private set
    var managers: Set<SLPlayerId> = nationOutpost.managerIds; private set
    var trustedPlayers: Set<SLPlayerId> = nationOutpost.trustedPlayerIds; private set
    var trustedNations: Set<Oid<Nation>> = nationOutpost.trustedNationIds; private set

    override fun contains(x: Int, y: Int, z: Int): Boolean {
        return distanceSquared(this.x.d(), 0.0, this.z.d(), x.d(), 0.0, z.d()) <= radius.toDouble().squared()
    }

    override fun update(delta: ChangeStreamDocument<NationOutpost>) {
        delta[NationOutpost::name]?.let { name = it.string() }
        delta[NationOutpost::worldName]?.let { world = it.string() }
        delta[NationOutpost::centerX]?.let { x = it.int() }
        delta[NationOutpost::centerZ]?.let { z = it.int() }
        delta[NationOutpost::radius]?.let { radius = it.int() }
        delta[NationOutpost::nationId]?.let { nationId = it.oid() }
        delta[NationOutpost::trustLevel]?.let { trustLevel = it.enumValue() }
        delta[NationOutpost::managerIds]?.let { col -> managers = col.mappedSet { it.slPlayerId() } }
        delta[NationOutpost::trustedPlayerIds]?.let { col -> trustedPlayers = col.mappedSet { it.slPlayerId() } }
        delta[NationOutpost::trustedNationIds]?.let { col -> trustedNations = col.mappedSet { it.oid<Nation>() } }
    }

    override fun calculateInaccessMessage(player: Player): String? {
        val playerData = PlayerCache[player]

        val playerNation: Oid<Nation>? = playerData.nationId

        // if they're in a nation, check for trust level auto perms, and trusted nations
        if (playerNation != null) {
            // if trust level is nation and they're in the same nation
            if (trustLevel == NationOutpost.TrustLevel.NATION && nationId == playerNation) {
                return null
            }

            // if they're at least an ally and trust level is ally (should cover same nation)
            if (trustLevel == NationOutpost.TrustLevel.ALLY && RelationCache[playerNation, nationId] >= NationRelation.Level.ALLY) {
                return null
            }

            // if they're in a trusted nation
            if (trustedNations.contains(playerNation)) {
                return null
            }
        }

        // if they're a manager they can build
        if (managers.contains(playerData.id)) {
            return null
        }

        if (trustedPlayers.contains(playerData.id)) {
            return null
        }

        return "&cSpace station $name is claimed by ${NationCache[nationId].name} @ $x,$z x $radius"
    }
}
