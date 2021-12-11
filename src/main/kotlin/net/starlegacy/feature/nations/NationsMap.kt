package net.starlegacy.feature.nations

import net.starlegacy.SLComponent
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.feature.nations.region.types.RegionNationOutpost
import net.starlegacy.util.Tasks
import org.bukkit.Bukkit
import org.bukkit.Color
import org.dynmap.bukkit.DynmapPlugin
import org.dynmap.markers.*

@Suppress("unused", "MemberVisibilityCanBePrivate")
object NationsMap : SLComponent() {
    private fun syncOnly(block: () -> Unit) = when {
        Bukkit.isPrimaryThread() -> block()
        else -> Tasks.sync(block)
    }

    private val dynmapLoaded by lazy { Bukkit.getPluginManager().isPluginEnabled("dynmap") }

    private val markerAPI: MarkerAPI get() = DynmapPlugin.plugin.markerAPI

    private val markerSet
        get() = markerAPI.getMarkerSet("nations")
            ?: markerAPI.createMarkerSet("nations", "Nations, Settlements, & Stations", null, false)

    override fun onEnable() {
        if (!dynmapLoaded) {
            log.warn("Dynmap not loaded!")
        }

        reloadDynmap()
    }

    override fun onDisable() {
        // empty
    }

    fun reloadDynmap() = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        markerSet.layerPriority = 100

        markerSet.areaMarkers.forEach(AreaMarker::deleteMarker)
        markerSet.markers.forEach(Marker::deleteMarker)

        // map has to load before other components so do this a tick later
        Tasks.sync {
            SettlementCache.getAll().forEach(::addSettlement)
            TerritoryCache.getAll().forEach(::addTerritory)
        }
    }

    fun updateOwners() = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        SettlementCache.getAll().forEach(NationsMap::updateSettlement)
        TerritoryCache.getAll().forEach(NationsMap::updateTerritory)
    }

    fun addSettlement(settlement: Settlement): Unit = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        try {
            removeSettlement(settlement)

            if (Bukkit.getWorld(settlement.worldName) == null) {
                return@syncOnly
            }

            SettlementMapper(markerSet, settlement).run { marker ->
                marker.label = settlement.name

                var rgb: Int = Color.BLUE.asRGB()
                var fillOpacity = 0.2
                var lineOpacity = 0.5

                val nation: Nation? = settlement.nationId?.let(NationCache::get)
                if (nation != null) {
                    rgb = nation.color
                    fillOpacity = 0.2
                    lineOpacity = 0.5
                    marker.label += " (${nation.name})"
                }

                marker.setFillStyle(fillOpacity, rgb)

                val lineThickness = 3
                marker.setLineStyle(lineThickness, lineOpacity, rgb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeSettlement(settlement: Settlement): Unit = syncOnly {
        for (marker in markerSet.areaMarkers) {
            if (!marker.markerID.contains(settlement._id.toString())) {
                continue
            }

            marker.deleteMarker()
        }
    }

    fun updateSettlement(settlement: Settlement): Unit = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        removeSettlement(settlement)
        addSettlement(settlement)
    }

    fun addTerritory(territory: Territory): Unit = syncOnly {
        removeTerritory(territory)

        val id = "territory-${territory._id}"
        val name = territory.name
        val world = territory.worldName
        val x = territory.x
        val y = 128.0
        val z = territory.z.toDouble()
        val radius = territory.radius.toDouble()

        markerSet.createCircleMarker(id, name, false, world, x.toDouble(), y, z, radius, radius, false)

        updateTerritory(territory)
    }

    fun removeTerritory(territory: Territory) = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        markerSet.findAreaMarker("territory-${territory._id}")?.deleteMarker()
    }

    fun updateTerritory(territory: Territory): Unit = syncOnly {
        if (!dynmapLoaded) {
            return@syncOnly
        }

        val marker: CircleMarker = markerSet.findCircleMarker("territory-${territory._id}")
            ?: return@syncOnly addTerritory(territory)

        updateBastions(territory)

        val nation = territory.nationId?.let(NationCache::get)

        val rgb = nation?.color ?: Color.WHITE.asRGB()
        marker.setFillStyle(0.0, Color.WHITE.asRGB())
        marker.setLineStyle(5, 0.8, rgb)

        marker.description = """
                <p><h2>${territory.name}</h2></p>
                <p>
                <h3>Owned by ${nation?.name ?: "no nation"}</h3>
                <p>Siege time: ${territory.siegeSchedule.period.text} on ${territory.siegeSchedule.dayOfWeek} (EST)</p>
            """.trimIndent()
    }

    private fun updateBastions(territory: Territory) {
        markerSet.markers
            .filter { it.markerID.startsWith("bastion-${territory._id}") }
            .toList()
            .forEach { it.deleteMarker() }

        for (bastion in territory.bastions) {
            val id = "bastion-${territory._id}-${bastion.name}"
            val occupierName = bastion.occupierId?.let(NationCache::get)?.name ?: "Not claimed"
            val name = "${bastion.name} (${occupierName})"
            val world = territory.worldName
            val x = bastion.x.toDouble()
            val y = bastion.y.toDouble()
            val z = bastion.z.toDouble()
            val icon = markerAPI.getMarkerIcon(if (bastion.occupierId == territory.nationId) "bricks" else "pirateflag")
            markerSet.createMarker(id, name, false, world, x, y, z, icon, false)
        }
    }

    private fun getMarkerID(station: RegionNationOutpost) = "nation-station-" + station.dbObjectId.toString()

    override fun supportsVanilla(): Boolean {
        return true
    }
}
