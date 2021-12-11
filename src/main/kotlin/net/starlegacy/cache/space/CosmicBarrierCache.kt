package net.starlegacy.cache.space

import net.starlegacy.database.Oid
import net.starlegacy.database.schema.space.CosmicBarrier

object CosmicBarrierCache : AbstractCelestialBodyCache<CosmicBarrier>(CosmicBarrier.Companion) {
    override val idAttribute = propertyAttribute<CosmicBarrier, Oid<out CosmicBarrier>>(CosmicBarrier::_id)
    override val lowercaseNameAttribute = attribute<CosmicBarrier, String> { it.name.toLowerCase() }
}
