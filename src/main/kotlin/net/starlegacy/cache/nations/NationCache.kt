package net.starlegacy.cache.nations

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.QueryFactory.equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.schema.nations.Nation

object NationCache : DbObjectCache<Nation>(Nation.Companion) {
    override val idAttribute = idAttribute(Nation::_id)
    private val nameAttribute = attribute<Nation, String> { it.name.toLowerCase() }

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nameAttribute))
    }

    fun getByName(name: String): Nation? {
        return cache.retrieve(equal(nameAttribute, name.toLowerCase())).firstOrNull()
    }
}
