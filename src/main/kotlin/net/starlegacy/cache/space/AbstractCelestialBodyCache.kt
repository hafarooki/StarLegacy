package net.starlegacy.cache.space

import com.googlecode.cqengine.attribute.SimpleAttribute
import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.simple.Equal
import net.starlegacy.cache.DbObjectCache
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.schema.space.CelestialBody
import net.starlegacy.feature.space.SpaceMap

abstract class AbstractCelestialBodyCache<T : CelestialBody>(
    companion: OidDbObjectCompanion<T>
) : DbObjectCache<T>(companion) {
    protected abstract val lowercaseNameAttribute: SimpleAttribute<T, String>

    override fun onInsert(cached: T) {
        super.onInsert(cached)
        SpaceMap.refresh()
    }

    override fun onUpdate(old: T, new: T) {
        super.onUpdate(old, new)
        SpaceMap.refresh()
    }

    override fun onDelete(cached: T) {
        super.onDelete(cached)
        SpaceMap.refresh()
    }

    override fun addExtraIndexes() {
        super.addExtraIndexes()
        cache.addIndex(HashIndex.onAttribute(lowercaseNameAttribute))
    }

    fun getByName(name: String): T? {
        return cache.retrieve(Equal(lowercaseNameAttribute, name.toLowerCase())).firstOrNull()
    }
}
