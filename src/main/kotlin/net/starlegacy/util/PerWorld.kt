package net.starlegacy.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.bukkit.World

class PerWorld<T : Any>(newT: (World) -> T) {
    val cache: LoadingCache<World, T> = CacheBuilder.newBuilder()
        .weakKeys()
        .build(CacheLoader.from { world -> newT(world!!) })

    operator fun get(world: World): T = cache[world]
}
