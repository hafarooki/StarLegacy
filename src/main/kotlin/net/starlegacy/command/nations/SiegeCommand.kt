package net.starlegacy.command.nations

import net.starlegacy.command.SLCommand
import net.starlegacy.feature.nations.Sieges
import co.aikar.commands.annotation.CommandAlias
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionTerritory
import org.bukkit.entity.Player

internal object SiegeCommand : SLCommand() {
    @CommandAlias("siege")
    fun execute(sender: Player, bastionName: String) {
        val territory = requireTerritoryIn(sender)
        val bastion = retrieveBastion(territory, bastionName)

        Sieges.beginSiege(sender, territory, bastion)
    }

    private fun requireTerritoryIn(sender: Player): Territory {
        val territoryRegion = Regions.findFirstOf<RegionTerritory>(sender.location)
            ?: fail { "You are not in a territory's region!" }

        return TerritoryCache[territoryRegion.dbObjectId]
    }

    private fun retrieveBastion(territory: Territory, bastionName: String): Territory.Bastion {
        return territory.bastions.firstOrNull { it.name.equals(bastionName, ignoreCase = true) }
            ?: fail {
                "${territory.name} does not have a bastion named $bastionName;" +
                        " its bastions are ${territory.bastions.joinToString { it.name }}"
            }
    }
}
