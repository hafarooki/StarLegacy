package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.database.schema.space.CelestialBody
import net.starlegacy.feature.space.celestialbody.visualizer.ChunkCacheBuilder
import net.starlegacy.util.Vec3i
import org.bukkit.Bukkit
import org.bukkit.World

abstract class CelestialBodyInstance<T : CelestialBody>(val celestialBody: T) {
    abstract val spaceWorldName: String

    abstract val position: Vec3i

    val spaceWorld: World?
        get() = Bukkit.getWorld(spaceWorldName)

    val location get() = position.toLocation(checkNotNull(spaceWorld))

    abstract fun isChunkInRange(world: World, chunkX: Int, chunkZ: Int): Boolean

    abstract fun populateChunk(builder: ChunkCacheBuilder)
}
