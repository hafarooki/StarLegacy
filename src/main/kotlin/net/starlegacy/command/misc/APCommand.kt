package net.starlegacy.command.misc

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.multiblock.defenseturret.APTurretMultiblock
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.Region
import net.starlegacy.feature.nations.region.types.RegionTerritory
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.nations.region.types.RegionNationOutpost
import net.starlegacy.util.msg
import org.bukkit.entity.Player
import java.util.*

@CommandAlias("ap")
object APCommand : SLCommand() {
    @Subcommand("add|set|a|+")
    @CommandCompletion("@players")
    fun onAdd(sender: Player, target: String) = asyncCommand(sender) {
        val playerId: UUID = resolveOfflinePlayer(target)
        val slPlayer = SLPlayer[playerId.slPlayerId] ?: fail {
            "Player not in database"
        }

        val targetSettlement = slPlayer.settlementId
        failIf(targetSettlement != null && PlayerCache[sender].settlementId == targetSettlement) {
            "Cannot target settlement members"
        }

        val targetNation = slPlayer.nationId?.let(NationCache::get)
        failIf(targetNation != null && getRelation(sender, targetNation) >= NationRelation.Level.ALLY) {
            "Cannot target allies"
        }

        for (region in Regions.find(sender.location)) {
            if (!isDefendedRegion(region)) {
                continue
            }

            if (!region.canAccess(sender)) {
                sender msg "&cYou need build access to control targets"
                continue
            }

            if (!APTurretMultiblock.regionalTargets[region.dbObjectId].add(playerId)) {
                sender msg "&c$target was already targeted"
                continue
            }

            sender msg "&aTargeted $target"

            APTurretMultiblock.regionalTargets[region.dbObjectId].add(playerId)
        }
    }

    private fun isDefendedRegion(region: Region<*>): Boolean {
        return region is RegionSettlement || region is RegionNationOutpost || region is RegionTerritory
    }

    @Subcommand("remove|unset|r|-")
    @CommandCompletion("@players")
    fun onRemove(sender: Player, target: String) = asyncCommand(sender) {
        val playerId: UUID = resolveOfflinePlayer(target)

        for (region in Regions.find(sender.location)) {
            if (!isDefendedRegion(region)) {
                continue
            }

            if (!region.canAccess(sender)) {
                sender msg "You need build access to control targets"
                continue
            }

            if (!APTurretMultiblock.regionalTargets[region.dbObjectId].remove(playerId)) {
                sender msg "&c$target was not targeted"
                continue
            }

            sender msg "&aUn-targeted $target"
        }
    }
}
