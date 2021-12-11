package net.starlegacy.cache.trade

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.simple.Equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.economy.EcoStation

object EcoStationCache : DbObjectCache<EcoStation>(EcoStation.Companion) {
    override val idAttribute = idAttribute(EcoStation::_id)
    private val nameAttribute = attribute<EcoStation, String> { it.name.toLowerCase() }

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nameAttribute))
    }

    fun getByName(name: String): EcoStation? {
        return cache.retrieve(Equal(nameAttribute, name.toLowerCase())).firstOrNull()
    }
}
