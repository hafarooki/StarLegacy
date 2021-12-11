package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.database.schema.space.Star
import net.starlegacy.util.Vec3i
import org.bukkit.block.data.BlockData
import org.bukkit.util.noise.SimplexNoiseGenerator

class StarInstance(
    celestialBody: Star
) : SphericalCelestialBodyInstance<Star>(celestialBody) {
    override val spaceWorldName = celestialBody.spaceWorld
    override val position get() = Vec3i(celestialBody.x, celestialBody.y, celestialBody.z)

    private val classification = celestialBody.classification

    private val noiseGenerator1 = SimplexNoiseGenerator(celestialBody.name.hashCode().toLong())
    private val noiseGenerator2 = SimplexNoiseGenerator(celestialBody.hashCode().toLong() + 10)

    override val spheres = listOf(
        SphereData(classification.radius - 2) { _, _, _ -> classification.surfaceData.random() },
        SphereData(classification.radius - 1) { dx, dy, dz -> getCoronaBlock(dx, dy, dz, noiseGenerator1) },
        SphereData(classification.radius) { dx, dy, dz -> getCoronaBlock(dx, dy, dz, noiseGenerator2) },
    )

    private fun getCoronaBlock(dx: Int, dy: Int, dz: Int, noiseGenerator: SimplexNoiseGenerator): BlockData {
        val x = dx.toDouble()
        val y = dy.toDouble()
        val z = dz.toDouble()
        val noise = noiseGenerator.noise(x / 5, y / 5, z / 5) / 2.0 + 0.5
        val index = (noise * classification.haloData.size).toInt() % classification.haloData.size
        return classification.haloData[index]
    }
}
