package net.starlegacy.cache.nations

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.QueryFactory.equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.feature.nations.NationsMap

object TerritoryCache : DbObjectCache<Territory>(Territory.Companion) {
    override val idAttribute = idAttribute(Territory::_id)
    private val nameAttribute = attribute<Territory, String> { it.name.toLowerCase() }
    private val nationIdAttribute = nullableAttribute<Territory, Oid<Nation>>(Territory::nationId)
    private val worldAttribute = propertyAttribute(Territory::worldName)

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nameAttribute))
        cache.addIndex(HashIndex.onAttribute(nationIdAttribute))
        cache.addIndex(HashIndex.onAttribute(worldAttribute))
    }

    fun getByName(name: String): Territory? {
        return cache.retrieve(equal(nameAttribute, name.toLowerCase())).firstOrNull()
    }

    fun getByNationId(nationId: Oid<Nation>): List<Territory> {
        return cache.retrieve(equal(nationIdAttribute, nationId)).toList()
    }

    fun getByWorld(world: String): List<Territory> {
        return cache.retrieve(equal(worldAttribute, world)).toList()
    }

    override fun onInsert(cached: Territory) {
        NationsMap.addTerritory(cached)
    }

    override fun onUpdate(old: Territory, new: Territory) {
        NationsMap.updateTerritory(new)
    }

    override fun onDelete(cached: Territory) {
        NationsMap.removeTerritory(cached)
    }
}
