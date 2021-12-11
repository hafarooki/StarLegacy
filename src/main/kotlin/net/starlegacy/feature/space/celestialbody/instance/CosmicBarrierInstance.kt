package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.database.schema.space.CosmicBarrier
import net.starlegacy.feature.space.celestialbody.visualizer.ChunkCacheBuilder
import net.starlegacy.util.Vec3i
import net.starlegacy.util.d
import net.starlegacy.util.distanceSquared
import net.starlegacy.util.squared
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.util.noise.SimplexNoiseGenerator
import kotlin.math.min
import kotlin.math.sqrt

class CosmicBarrierInstance(
    celestialBody: CosmicBarrier
) : CelestialBodyInstance<CosmicBarrier>(celestialBody) {
    companion object {
        const val RING_THICKNESS = 256
        const val HALF_RING_THICKNESS = RING_THICKNESS / 2
    }

    override val spaceWorldName = celestialBody.spaceWorld
    override val position = Vec3i(celestialBody.x, celestialBody.y, celestialBody.z)

    private val radius = celestialBody.radius
    private val blockDataList = listOf(
        Material.BLUE_STAINED_GLASS.createBlockData(),
        Material.CYAN_STAINED_GLASS.createBlockData(),
        Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(),
        Material.PURPLE_STAINED_GLASS.createBlockData(),
    )

    private val allowedChunkRange =
        (radius / 16 - (RING_THICKNESS / 2 / 16 + 1)).squared()..(radius / 16 + (RING_THICKNESS / 2 / 16 + 1)).squared()

    override fun isChunkInRange(world: World, chunkX: Int, chunkZ: Int): Boolean {
        val (x0, _, z0) = this.position
        val distanceSquared = distanceSquared(x0 shr 4, 0, z0 shr 4, chunkX, 0, chunkZ)
        return distanceSquared in allowedChunkRange
    }

    override fun populateChunk(builder: ChunkCacheBuilder) {
        val (x0, y0, z0) = this.position

        val chunkBaseX = builder.chunkX shl 4
        val chunkBaseZ = builder.chunkZ shl 4

        val halfRingThickness = RING_THICKNESS / 2

        val noise = SimplexNoiseGenerator(celestialBody.name.hashCode().toLong())

        for (x in (chunkBaseX)..(chunkBaseX + 15)) {
            for (y in 0..255) {
                for (z in (chunkBaseZ)..(chunkBaseZ + 15)) {
                    val horizontalDivergence = sqrt((x - x0).d().squared() + (z - z0).d().squared()) - radius
                    val verticalDivergence = (y - y0).toDouble()
                    val divergenceSquared = (horizontalDivergence.squared() + verticalDivergence.squared())
                    val divergence = sqrt(divergenceSquared)

                    if (divergence > halfRingThickness) {
                        continue
                    }

                    val chance: Double = (1.0 - min(1.0, divergence / halfRingThickness)) * 0.2

                    if (noise.noise(x.d(), y.d(), z.d()) / 2 + 0.5 > chance) {
                        continue
                    }

                    val blockData = blockDataList.random()
                    builder.setBlockGlobal(x, y, z, blockData)
                }
            }
        }
    }
}
