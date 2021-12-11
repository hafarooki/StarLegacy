package net.starlegacy.feature.space.celestialbody.instance

import net.starlegacy.database.schema.space.BlackHole
import net.starlegacy.util.Vec3i
import org.bukkit.Material

class BlackHoleInstance(
    celestialBody: BlackHole
) : SphericalCelestialBodyInstance<BlackHole>(celestialBody) {
    override val spaceWorldName: String = celestialBody.spaceWorld
    override val position: Vec3i = Vec3i(celestialBody.x, celestialBody.y, celestialBody.z)

    private val blackConcrete = Material.BLACK_CONCRETE.createBlockData()
    private val blackStainedGlass = Material.BLACK_STAINED_GLASS.createBlockData()

    override val spheres = listOf(
        SphereData(celestialBody.radius - 1) { _, _, _ -> blackConcrete },
        SphereData(celestialBody.radius) { _, _, _ -> blackStainedGlass },
    )
}
