package net.starlegacy.cache.space

import net.starlegacy.database.schema.space.Star

object StarCache : AbstractCelestialBodyCache<Star>(Star.Companion) {
    override val idAttribute = idAttribute(Star::_id)
    override val lowercaseNameAttribute = attribute<Star, String> { it.name.toLowerCase() }
}
