package net.starlegacy.cache.nations

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.QueryFactory.equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.feature.nations.NationsMap
import net.starlegacy.util.Tasks

object SettlementCache : DbObjectCache<Settlement>(Settlement.Companion) {
    override val idAttribute = idAttribute(Settlement::_id)
    private val nameAttribute = attribute<Settlement, String> { it.name.toLowerCase() }
    private val worldAttribute = propertyAttribute(Settlement::worldName)

    override fun addExtraIndexes() {
        cache.addIndex(HashIndex.onAttribute(nameAttribute))
        cache.addIndex(HashIndex.onAttribute(worldAttribute))
    }

    fun getByName(name: String): Settlement? {
        return cache.retrieve(equal(nameAttribute, name.toLowerCase())).firstOrNull()
    }

    fun listByWorld(world: String): List<Settlement> {
        return cache.retrieve(equal(worldAttribute, world)).toList()
    }

    override fun onInsert(cached: Settlement) {
        // Delay a tick so the other caches update first
        Tasks.sync {
            NationsMap.addSettlement(cached)
        }
    }

    override fun onUpdate(old: Settlement, new: Settlement) {
        // Delay a tick so the other caches update first
        Tasks.sync {
            NationsMap.updateSettlement(new)
        }
    }

    override fun onDelete(cached: Settlement) {
        // Delay a tick so the other caches update first
        Tasks.sync {
            NationsMap.removeSettlement(cached)
        }
    }
}
