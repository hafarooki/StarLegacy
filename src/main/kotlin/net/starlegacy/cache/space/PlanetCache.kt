package net.starlegacy.cache.space

import com.googlecode.cqengine.index.hash.HashIndex
import com.googlecode.cqengine.query.simple.Equal
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.space.Planet
import org.bukkit.World

object PlanetCache : AbstractCelestialBodyCache<Planet>(Planet.Companion) {
    override val idAttribute = propertyAttribute<Planet, Oid<out Planet>>(Planet::_id)
    override val lowercaseNameAttribute = attribute<Planet, String> { it.name.toLowerCase() }
    private val planetWorldAttribute = attribute<Planet, String> { it.planetWorld.toLowerCase() }

    override fun addExtraIndexes() {
        super.addExtraIndexes()
        cache.addIndex(HashIndex.onAttribute(planetWorldAttribute))
    }

    fun getByPlanetWorldName(planetWorldName: String): Planet? {
        return cache.retrieve(Equal(planetWorldAttribute, planetWorldName.toLowerCase()))?.firstOrNull()
    }

    fun getByPlanetWorld(planetWorld: World): Planet? {
        return getByPlanetWorldName(planetWorld.name)
    }
}
