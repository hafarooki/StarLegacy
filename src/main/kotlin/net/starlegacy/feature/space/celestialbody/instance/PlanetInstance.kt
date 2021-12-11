package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.cache.space.StarCache
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.database.schema.space.Star
import net.starlegacy.feature.misc.CustomItem
import net.starlegacy.feature.misc.CustomItems
import net.starlegacy.util.Vec3i
import net.starlegacy.util.d
import net.starlegacy.util.i
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.util.noise.SimplexNoiseGenerator
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

class PlanetInstance(
    celestialBody: Planet
) : SphericalCelestialBodyInstance<Planet>(celestialBody) {
    val sun get() = StarCache.getById(celestialBody.sun)

    override val spaceWorldName get() = sun.spaceWorld
    override val position = calculateLocation(sun, celestialBody.orbitDistance, celestialBody.orbitSpeed)

    private fun calculateLocation(sun: Star, orbitDistance: Int, orbitSpeed: Double): Vec3i {
        val (x, y, z) = sun.instance.position

        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toDouble()
        val orbitProgress = orbitSpeed * days

        val radians = Math.toRadians(orbitProgress)

        return Vec3i(
            x = x + (cos(radians) * orbitDistance.d()).i(),
            y = y,
            z = z + (sin(radians) * orbitDistance.d()).i()
        )
    }

    val planetIcon: CustomItem? = CustomItems["planet_icon_${celestialBody.name.toLowerCase().replace(" ", "")}"]

    private val crustMaterials: List<BlockData> = celestialBody.crustMaterials
        .map { Material.getMaterial(it) ?: error("No material $it!") }
        .map(Material::createBlockData)

    private val cloudMaterials: List<BlockData> = celestialBody.cloudMaterials
        .map { Material.getMaterial(it) ?: error("No material $it!") }
        .map(Bukkit::createBlockData)

    private val random = SimplexNoiseGenerator(celestialBody.seed)

    override val spheres = listOf(
        SphereData(celestialBody.crustRadius, ::getCrustBlock),
        SphereData(celestialBody.atmosphereRadius, ::getAtmosphereBlock),
    )

    private fun getCrustBlock(x: Int, y: Int, z: Int): BlockData {
        // number from -1 to 1
        val simplexNoise = random.noise(
            x.d() * celestialBody.crustNoise,
            y.d() * celestialBody.crustNoise,
            z.d() * celestialBody.crustNoise
        )

        val noise = (simplexNoise / 2.0 + 0.5)

        return when {
            crustMaterials.isEmpty() -> Material.DIRT.createBlockData()
            else -> crustMaterials[(noise * crustMaterials.size).toInt()]
        }
    }

    private fun getAtmosphereBlock(x: Int, y: Int, z: Int): BlockData? {
        if (cloudMaterials.isEmpty()) {
            return null
        }

        val atmosphereSimplex = random.noise(
            x.d() * celestialBody.cloudDensityNoise,
            y.d() * celestialBody.cloudDensityNoise,
            z.d() * celestialBody.cloudDensityNoise
        )

        if ((atmosphereSimplex / 2.0 + 0.5) > celestialBody.cloudDensity) {
            return null
        }

        val cloudSimplex = random.noise(
            -x.d() * celestialBody.cloudNoise,
            -y.d() * celestialBody.cloudNoise,
            -z.d() * celestialBody.cloudNoise
        )

        val noise = (cloudSimplex / 2.0) + 0.5

        if (noise > celestialBody.cloudThreshold) {
            return null
        }

        return cloudMaterials[(noise * cloudMaterials.size).toInt()]
    }
}
