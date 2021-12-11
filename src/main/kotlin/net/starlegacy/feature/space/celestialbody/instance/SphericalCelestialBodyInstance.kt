package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.database.schema.space.CelestialBody
import net.starlegacy.feature.space.celestialbody.visualizer.ChunkCacheBuilder
import net.starlegacy.util.distanceSquared
import net.starlegacy.util.squared
import org.bukkit.World
import org.bukkit.block.data.BlockData

abstract class SphericalCelestialBodyInstance<T : CelestialBody>(
    celestialBody: T
) : CelestialBodyInstance<T>(celestialBody) {
    protected data class SphereData(
        val radius: Int,
        val getBlock: (Int, Int, Int) -> BlockData?
    ) {
        val lowerBound = (radius - 1).squared()
        val upperBound = (radius).squared()
    }

    protected abstract val spheres: List<SphereData>

    private val maxRangeSquared by lazy {
        val maxRadius = checkNotNull(spheres.maxOf(SphereData::radius))
        return@lazy (maxRadius / 16 + 2).squared()
    }

    override fun isChunkInRange(world: World, chunkX: Int, chunkZ: Int): Boolean {
        if (world != this.spaceWorld) {
            return false
        }

        val (x0, _, z0) = this.position
        val distanceSquared = distanceSquared(x0 shr 4, 0, z0 shr 4, chunkX, 0, chunkZ)
        return distanceSquared <= maxRangeSquared
    }

    override fun populateChunk(builder: ChunkCacheBuilder) {
        val (x0, y0, z0) = this.position

        val chunkBaseX = builder.chunkX shl 4
        val chunkBaseZ = builder.chunkZ shl 4

        val lowestLowerBound: Int = spheres.minOf { it.lowerBound }
        val highestUpperBound: Int = spheres.maxOf { it.upperBound }

        for (x in (chunkBaseX)..(chunkBaseX + 15)) {
            for (y in 0..255) {
                for (z in (chunkBaseZ)..(chunkBaseZ + 15)) {
                    val dx = x - x0
                    val dy = y - y0
                    val dz = z - z0
                    val distanceSquared = dx.squared() + dy.squared() + dz.squared()

                    // ignore blocks that are not within any of the spheres
                    if (distanceSquared < lowestLowerBound || distanceSquared >= highestUpperBound) {
                        continue
                    }

                    for (sphere in spheres) {
                        // ignore blocks not part of this specific sphere
                        if (distanceSquared < sphere.lowerBound || distanceSquared >= sphere.upperBound) {
                            continue
                        }

                        val blockData = sphere.getBlock(dx, dy, dz)
                            ?: continue

                        builder.setBlockGlobal(x, y, z, blockData)
                        break // one sphere per block
                    }
                }
            }
        }
    }
}
