package net.starlegacy.feature.starship.hyperspace

import net.starlegacy.cache.space.CosmicBarrierCache
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.util.distance
import net.starlegacy.util.distanceSquared
import net.starlegacy.util.squared
import org.bukkit.World
import kotlin.math.sqrt

object MassShadows {
    private const val PLANET_RADIUS = 1000
    private const val STAR_RADIUS = 1800

    data class MassShadowInfo(val description: String, val x: Int, val z: Int, val radius: Int, val distance: Int)

    fun find(world: World, x: Double, z: Double): MassShadowInfo? {
        val realWorld = (if (Hyperspace.isHyperspaceWorld(world)) Hyperspace.getRealspaceWorld(world) else world)
            ?: return null

        for (planet in PlanetCache.getAll()) {
            if (planet.instance.spaceWorld != realWorld) {
                continue
            }
            val loc = planet.instance.position
            var dist = distanceSquared(x, 128.0, z, loc.x.toDouble(), 128.0, loc.z.toDouble())
            if (dist > PLANET_RADIUS.squared()) {
                continue
            }
            dist = sqrt(dist)
            return MassShadowInfo("Planet ${planet.name}", loc.x, loc.z, PLANET_RADIUS, dist.toInt())
        }

        for (star in StarCache.getAll()) {
            if (star.instance.spaceWorld != realWorld) {
                continue
            }
            val loc = star.instance.position
            var dist = distanceSquared(x, 128.0, z, loc.x.toDouble(), 128.0, loc.z.toDouble())
            if (dist > STAR_RADIUS.squared()) {
                continue
            }
            dist = sqrt(dist)
            return MassShadowInfo("Star ${star.name}", loc.x, loc.z, STAR_RADIUS, dist.toInt())
        }

        for (cosmicBarrier in CosmicBarrierCache.getAll()) {
            if (cosmicBarrier.instance.spaceWorld != realWorld) {
                continue
            }
            val loc = cosmicBarrier.instance.position
            var dist = distance(x, 128.0, z, loc.x.toDouble(), 128.0, loc.z.toDouble())
            val radius = cosmicBarrier.radius
            if (dist !in (radius - 256.0)..(radius + 256.0)) {
                continue
            }
            dist = sqrt(dist)
            val name = cosmicBarrier.name
            return MassShadowInfo("Cosmic Barrier $name", loc.x, loc.z, radius, dist.toInt())
        }

        for (otherShip in ActiveStarships.getInWorld(realWorld)) {
            if (!otherShip.isInterdicting) {
                continue
            }

            val otherX = otherShip.centerOfMass.x
            val otherY = otherShip.centerOfMass.y
            val otherZ = otherShip.centerOfMass.z
            var dist = distanceSquared(x, 128.0, z, otherX.toDouble(), otherY.toDouble(), otherZ.toDouble())
            if (dist > otherShip.interdictionRange.squared()) {
                continue
            }
            dist = sqrt(dist)
            return MassShadowInfo("Anomaly", otherX, otherY, otherZ, dist.toInt())
        }

        return null
    }
}
