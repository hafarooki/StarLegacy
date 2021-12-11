package net.starlegacy.feature.nations.region.types

import com.mongodb.client.model.changestream.ChangeStreamDocument
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.database.*
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.util.d
import net.starlegacy.util.distanceSquared
import org.bukkit.entity.Player

class RegionTerritory(station: Territory) : Region<Territory>(station),
    RegionTopLevel {
    override val priority: Int = 0

    override var world: String = station.worldName
    private var name: String = station.name
    private var x: Int = station.x
    private var z: Int = station.z
    private var radius: Int = station.radius
    private var nationId: Oid<Nation>? = station.nationId

    override fun contains(x: Int, y: Int, z: Int): Boolean {
        val radiusSquared = radius * radius
        return distanceSquared(x.d(), 0.0, z.d(), this.x.d(), 0.0, this.z.d()) <= radiusSquared
    }

    override fun update(delta: ChangeStreamDocument<Territory>) {
        delta[Territory::worldName]?.let { world = it.string() }
        delta[Territory::name]?.let { name = it.string() }
        delta[Territory::x]?.let { x = it.int() }
        delta[Territory::z]?.let { z = it.int() }
        delta[Territory::radius]?.let { radius = it.int() }
        delta[Territory::nationId]?.let { nationId = it.nullable()?.oid() }
    }

    override fun calculateInaccessMessage(player: Player): String? {
        val nation = nationId ?: return "$name is not claimed by any nation!".intern()

        // if they're not in a nation they can't access any nation outposts
        val playerNation = PlayerCache[player].nationId

        // if they're at least an ally they can build
        if (playerNation != null && RelationCache[playerNation, nation] >= NationRelation.Level.ALLY) {
            return null
        }

        return "$name is a territory claimed by ${NationCache[nation].name}".intern()
    }
}
