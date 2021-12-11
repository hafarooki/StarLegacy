package net.starlegacy.command.nations.admin

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.nations.*
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.nations.NationsBalancing
import net.starlegacy.feature.nations.NationsMap
import net.starlegacy.feature.nations.NationsMasterTasks
import net.starlegacy.feature.nations.utils.isActive
import net.starlegacy.feature.nations.utils.isInactive
import net.starlegacy.util.msg
import net.starlegacy.util.toCreditsString
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.litote.kmongo.*
import java.time.DayOfWeek
import kotlin.math.roundToInt

@CommandAlias("nadmin|nationsadmin")
@CommandPermission("nations.admin")
internal object NationAdminCommand : SLCommand() {
    @Subcommand("rebalance")
    fun onRebalance(sender: CommandSender) {
        NationsBalancing.reload()
        sender msg "&aRebalanced"
    }

    @Subcommand("refresh map")
    fun onRefreshMap(sender: CommandSender) {
        NationsMap.reloadDynmap()
        sender msg "Refreshed map"
    }

    @Subcommand("runtask money")
    fun onRunTaskIncome(sender: CommandSender) {
        NationsMasterTasks.executeMoneyTasks()
        sender msg "Executed income task"
    }

    @Subcommand("runtask purge")
    fun onRunTaskPurge(sender: CommandSender) = asyncCommand(sender) {
        NationsMasterTasks.checkPurges()
        sender msg "Executed purge task"
    }

    @Subcommand("player set settlement")
    fun onPlayerSetSettlement(sender: CommandSender, player: String, settlementName: String) = asyncCommand(sender) {
        val playerId = resolveOfflinePlayer(player).slPlayerId
        val settlement = resolveSettlement(settlementName)

        failIf(SLPlayer.isSettlementLeader(playerId))
        { "$player is the leader of a settlement, leader can't leave" }

        if (SLPlayer.matches(playerId, SLPlayer::settlementId ne null)) {
            SLPlayer.leaveSettlement(playerId)
        }

        SLPlayer.joinSettlement(playerId, settlement._id)

        sender msg "&aPut $player in ${settlement.name}"
    }

    private fun percentAndTotal(dividend: Double, divisor: Double) =
        "${(dividend / divisor * 100).roundToInt()}% ($dividend)"

    @Subcommand("player stats")
    fun onPlayerStats(sender: CommandSender) = asyncCommand(sender) {
        sender msg "Pulling from db..."
        val allPlayers = SLPlayer.all()
        val total = allPlayers.size.toDouble()
        sender msg "Analyzing $total players..."
        var playersInSettlements = 0.0
        var playersInNations = 0.0
        var activePlayers = 0.0
        var semiActivePlayers = 0.0
        var inactivePlayers = 0.0

        allPlayers.forEach {
            if (it.settlementId != null) playersInSettlements++
            if (it.nationId != null) playersInNations++

            when {
                isActive(it.lastSeen) -> activePlayers++
                isInactive(it.lastSeen) -> inactivePlayers++
                else -> semiActivePlayers++
            }
        }

        sender msg "&6Players in settlements: &b" + percentAndTotal(playersInSettlements, total)
        sender msg "&6Players in nations: &5" + percentAndTotal(playersInNations, total)
        sender msg "&6Active Players: &2" + percentAndTotal(activePlayers, total)
        sender msg "&6Semi-Active Players: &7" + percentAndTotal(semiActivePlayers, total)
        sender msg "&6Inactive Players: &c" + percentAndTotal(inactivePlayers, total)
    }

    @Subcommand("settlement set leader")
    fun onSettlementSetLeader(sender: CommandSender, settlementName: String, player: String) = asyncCommand(sender) {
        val settlement = resolveSettlement(settlementName)
        val playerId = resolveOfflinePlayer(player).slPlayerId
        requireIsMemberOf(playerId, settlement)
        Settlement.setLeader(settlement._id, playerId)
        sender msg "Changed leader of ${settlement.name} to ${getPlayerName(playerId)}"
    }

    @Subcommand("settlement purge")
    fun onSettlementPurge(sender: CommandSender, settlementName: String, sendMessage: Boolean) = asyncCommand(sender) {
        val settlement = resolveSettlement(settlementName)
        NationsMasterTasks.purgeSettlement(settlement, sendMessage)
        sender msg "Purged ${settlement.name}"
    }

    @Subcommand("settlement set balance")
    fun onSettlementSetBalance(sender: CommandSender, settlementName: String, balance: Int) = asyncCommand(sender) {
        val settlement = resolveSettlement(settlementName)
        Settlement.updateById(settlement._id, setValue(Settlement::balance, balance))
        sender msg "Set balance of ${settlement.name} to ${balance.toCreditsString()}"
    }

    @Subcommand("nation set balance")
    fun onNationSetBalance(sender: CommandSender, nationName: String, balance: Int) = asyncCommand(sender) {
        val nation = resolveNation(nationName)
        Nation.updateById(nation._id, setValue(Nation::balance, balance))
        sender msg "Set balance of ${nation.name} to ${balance.toCreditsString()}"
    }

    @Subcommand("nation outpost set location")
    fun onOutpostSetLocation(sender: CommandSender, stationName: String, world: World, x: Int, z: Int) =
        asyncCommand(sender) {
            val station = NationOutpost.findOne(NationOutpost::name eq stationName)
                ?: fail { "Station $stationName not found" }
            NationOutpost.updateById(
                station._id,
                set(
                    NationOutpost::worldName setTo world.name,
                    NationOutpost::centerX setTo x,
                    NationOutpost::centerZ setTo z
                )
            )
            sender msg "Set position of $stationName to $x, $z"
        }

    @Subcommand("territory create")
    fun onTerritoryCreate(
        sender: CommandSender,
        captureName: String,
        world: World,
        x: Int,
        z: Int,
        radius: Int,
        day: DayOfWeek,
        period: Territory.SiegePeriod
    ) = asyncCommand(sender) {
        Territory.create(captureName, world.name, x, z, radius, Territory.SiegeTime(day, period))
        sender msg "Created capture $captureName in world ${world.name} at $x, $z with radius $radius" +
                " (capture schedule: every week on $day during period $period)"
    }

    @Subcommand("territory list")
    fun onTerritoryList(
        sender: CommandSender,
    ) = asyncCommand(sender) {
        sender msg "Captures:"
        for (capture in Territory.all()) {
            val nationName = capture.nationId?.let { NationCache[it].name } ?: "no nation"
            sender msg "- ${capture.name}" +
                    " in ${capture.worldName} at ${capture.x}, ${capture.z}" +
                    " with radius ${capture.radius}" +
                    " owned by $nationName"
        }
    }

    @Subcommand("territory delete")
    fun onTerritoryDelete(
        sender: CommandSender,
        captureName: String,
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(captureName)
        Territory.delete(capture)
    }

    @Subcommand("territory bastion add")
    fun onTerritoryBastionAdd(
        sender: CommandSender,
        captureName: String,
        bastionName: String,
        x: Int,
        y: Int,
        z: Int
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(captureName)

        failIf(capture.bastions.any { it.name.equals(captureName, ignoreCase = true) }) {
            "A bastion of capture $captureName already has a name like $bastionName"
        }

        val bastion = Territory.Bastion(bastionName, x, y, z, capture.nationId)
        capture.bastions.add(bastion)

        Territory.save(capture)
        sender msg "Created bastion $bastionName at $x, $y, $z for $captureName"
    }

    @Subcommand("territory bastion list")
    fun onTerritoryBastionList(
        sender: CommandSender,
        captureName: String,
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(captureName)

        sender msg "Bastions of capture $captureName:"

        for (bastion in capture.bastions) {
            sender msg "- ${bastion.name}" +
                    " at ${bastion.x}, ${bastion.y}, ${bastion.z}" +
                    " occupied by ${bastion.occupierId?.let { NationCache[it].name } ?: "no nation"}"
        }
    }

    @Subcommand("territory bastion remove")
    fun onTerritoryBastionRemove(
        sender: CommandSender,
        captureName: String,
        bastionName: String,
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(captureName)

        val bastion = capture.bastions.firstOrNull { it.name.equals(bastionName, ignoreCase = true) }
        failIf(bastion == null) {
            "A bastion of capture $captureName with a name like $bastionName does not exist"
        }

        capture.bastions.remove(bastion)

        Territory.save(capture)
        sender msg "Removed bastion $bastionName from $captureName"
    }

    @Subcommand("territory reschedule")
    fun onTerritoryReschedule(
        sender: CommandSender,
        name: String,
        day: DayOfWeek,
        period: Territory.SiegePeriod
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(name)
        capture.siegeSchedule = Territory.SiegeTime(day, period)
        Territory.save(capture)
        sender msg "Rescheduled ${capture.name} to period $period on day $day"
    }

    @Subcommand("territory set radius")
    fun onTerritorySetRadius(
        sender: CommandSender,
        name: String,
        radius: Int,
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(name)
        capture.radius = radius
        Territory.save(capture)
        sender msg "Set radius of ${capture.name} to $radius"
    }

    @Subcommand("territory set location")
    fun onTerritorySetLocation(
        sender: CommandSender,
        name: String,
        world: World,
        x: Int,
        z: Int,
    ) = asyncCommand(sender) {
        val capture = retrieveCapture(name)
        capture.worldName = world.name
        capture.x = x
        capture.z = z
        Territory.save(capture)
        sender msg "Moved ${capture.name} to $x, $z in ${world.name}"
    }

    private fun retrieveCapture(name: String) = TerritoryCache.getByName(name)
        ?: fail { "Capture $name not found" }
}
