package net.starlegacy.cache.space

import net.starlegacy.database.Oid
import net.starlegacy.database.schema.space.BlackHole

object BlackHoleCache : AbstractCelestialBodyCache<BlackHole>(BlackHole.Companion) {
    override val idAttribute = propertyAttribute<BlackHole, Oid<out BlackHole>>(BlackHole::_id)
    override val lowercaseNameAttribute = attribute<BlackHole, String> { it.name.toLowerCase() }
}
