package net.starlegacy.feature.space

import net.starlegacy.SLComponent
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.util.Tasks
import org.bukkit.Color
import org.dynmap.bukkit.DynmapPlugin
import org.dynmap.markers.MarkerSet
import kotlin.random.Random

object SpaceMap : SLComponent() {
    private lateinit var markerSet: MarkerSet

    override fun onEnable() {
        Tasks.syncDelay(20) {
            refresh()
        }
    }

    fun refresh() = Tasks.sync {
        val markerAPI = DynmapPlugin.plugin.markerAPI

        markerAPI.getMarkerSet("space")?.deleteMarkerSet()
        markerSet = markerAPI.createMarkerSet("space", "Space", null, false)

        for (star in StarCache.getAll()) {
            markerSet.createMarker(
                star._id.toString(),
                star.name,
                star.spaceWorld,
                star.x.toDouble(),
                star.y.toDouble(),
                star.z.toDouble(),
                markerAPI.getMarkerIcon("sun"),
                false // ??
            )
        }

        for (planet in PlanetCache.getAll()) {
            val star = StarCache.getById(planet.sun)

            // planet icon
            markerSet.createMarker(
                planet._id.toString(),
                planet.name,
                planet.instance.spaceWorldName,
                planet.instance.position.x.toDouble(),
                planet.instance.position.y.toDouble(),
                planet.instance.position.z.toDouble(),
                markerAPI.getMarkerIcon("world"),
                false // ??
            )

            // planet ring
            markerSet.createCircleMarker(
                "${planet._id}_orbit",
                planet.name,
                false, // ??
                planet.instance.spaceWorldName,
                star.x.toDouble(),
                star.y.toDouble(),
                star.z.toDouble(),
                planet.orbitDistance.toDouble(),
                planet.orbitDistance.toDouble(),
                false // ??
            )?.run {
                setFillStyle(0.0, 0) // make the inside empty

                val random = Random(planet.name.hashCode())
                val r = random.nextInt(128, 255)
                val g = random.nextInt(1, 20)
                val b = random.nextInt(128, 255)
                val color = Color.fromRGB(r, g, b)
                setLineStyle(lineWeight, lineOpacity, color.asRGB())
            }
        }
    }

    override fun supportsVanilla(): Boolean {
        return true
    }
}
