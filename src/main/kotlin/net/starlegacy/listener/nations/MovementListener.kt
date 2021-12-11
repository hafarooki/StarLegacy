package net.starlegacy.listener.nations

import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.schema.nations.SettlementZone
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.Region
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.nations.region.types.RegionSettlementZone
import net.starlegacy.listener.SLEventListener
import net.starlegacy.util.Tasks
import net.starlegacy.util.action
import org.bukkit.ChatColor.GOLD
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.lang.System.currentTimeMillis
import java.util.*

object MovementListener : SLEventListener() {
    override fun supportsVanilla(): Boolean {
        return true
    }

    private val lastMoved = Collections.synchronizedMap(mutableMapOf<UUID, Long>())
    private val lastPlayerSettlements = Collections.synchronizedMap(mutableMapOf<UUID, Oid<Settlement>?>())
    private val lastPlayerZones = Collections.synchronizedMap(mutableMapOf<UUID, Oid<SettlementZone>?>())

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (lastMoved.containsKey(event.player.uniqueId)
            && currentTimeMillis() - lastMoved.getOrElse(event.player.uniqueId) { currentTimeMillis() } < 1000
        ) {
            return
        }

        lastMoved[event.player.uniqueId] = currentTimeMillis()

        val player: Player = event.player

        val newSettlementRegion: RegionSettlement? = Regions.findFirstOf(event.to)

        val uuid = player.uniqueId
        val oldSettlementId: Oid<Settlement>? = lastPlayerSettlements[uuid]

        if (oldSettlementId != newSettlementRegion?.dbObjectId) {
            lastPlayerSettlements[uuid] = newSettlementRegion?.dbObjectId

            if (newSettlementRegion != null) {
                val title = "${GOLD}Entered Settlement"
                val subtitle = newSettlementRegion.name
                player.sendTitle(title, subtitle, 20, 40, 20)
            } else if (oldSettlementId != null) {
                val title = "${GOLD}Exited Settlement"
                val subtitle = SettlementCache[oldSettlementId].name
                player.sendTitle(title, subtitle, 20, 40, 20)
            }
        }

        newSettlementRegion?.children?.firstOrNull { child ->
            child is RegionSettlementZone && child.contains(event.to.blockX, event.to.blockY, event.to.blockZ)
        }.also { zone: Region<*>? ->
            zone as RegionSettlementZone?

            val oldZone = lastPlayerZones[uuid]

            if (oldZone != zone?.dbObjectId) {
                lastPlayerZones[uuid] = zone?.dbObjectId

                if (zone != null) {
                    player action "&3Entered zone&b ${zone.name}"
                } else {
                    oldZone?.let { Regions.get<RegionSettlementZone>(it) }?.let {
                        player action "&3Exited zone&b ${it.name}"
                    }
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastMoved.remove(event.player.uniqueId)
        lastPlayerSettlements.remove(event.player.uniqueId)
    }
}
