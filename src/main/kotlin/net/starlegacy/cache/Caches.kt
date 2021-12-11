package net.starlegacy.cache

import net.starlegacy.SLComponent
import net.starlegacy.cache.nations.*
import net.starlegacy.cache.space.BlackHoleCache
import net.starlegacy.cache.space.CosmicBarrierCache
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.cache.trade.EcoStationCache

object Caches : SLComponent() {
    private val caches: List<Cache> = listOf(
        PlayerCache,
        SettlementCache,
        NationCache,
        NationOutpostCache,
        RelationCache,
        TerritoryCache,

        EcoStationCache,

        BlackHoleCache,
        CosmicBarrierCache,
        PlanetCache,
        StarCache,
    )

    override fun onEnable() = caches.forEach(Cache::load)

    override fun supportsVanilla(): Boolean {
        return true
    }
}
