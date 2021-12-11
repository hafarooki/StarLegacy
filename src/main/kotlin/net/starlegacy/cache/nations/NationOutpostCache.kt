package net.starlegacy.cache.nations

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.QueryFactory.equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationOutpost

object NationOutpostCache : DbObjectCache<NationOutpost>(NationOutpost.Companion) {
    override val idAttribute = idAttribute(NationOutpost::_id)
    private val nameAttribute = attribute<NationOutpost, String> { it.name.toLowerCase() }
    private val nationIdAttribute = propertyAttribute(NationOutpost::nationId)
    private val worldAttribute = propertyAttribute(NationOutpost::worldName)

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nameAttribute))
        cache.addIndex(HashIndex.onAttribute(nationIdAttribute))
        cache.addIndex(HashIndex.onAttribute(worldAttribute))
    }

    fun getByName(name: String): NationOutpost? {
        return cache.retrieve(equal(nameAttribute, name.toLowerCase())).firstOrNull()
    }

    fun listByNationId(nationId: Oid<Nation>): List<NationOutpost> {
        return cache.retrieve(equal(nationIdAttribute, nationId)).toList()
    }

    fun listByWorld(world: String): List<NationOutpost> {
        return cache.retrieve(equal(worldAttribute, world)).toList()
    }
}
