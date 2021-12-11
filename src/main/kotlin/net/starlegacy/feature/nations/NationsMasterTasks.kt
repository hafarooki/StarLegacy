package net.starlegacy.feature.nations

import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.NationOutpostCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationOutpost
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.uuid
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionSettlementZone
import net.starlegacy.feature.nations.utils.ACTIVE_AFTER_TIME
import net.starlegacy.feature.nations.utils.INACTIVE_BEFORE_TIME
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.gte
import kotlin.math.ceil
import kotlin.math.roundToInt

object NationsMasterTasks {
    fun executeAll() {
        checkPurges()

        executeMoneyTasks()
    }

    fun checkPurges() {
        for (settlement in SettlementCache.getAll()) {
            val id = settlement._id
            val query = and(SLPlayer::settlementId eq id, SLPlayer::lastSeen gte INACTIVE_BEFORE_TIME)

            if (SLPlayer.none(query)) {
                purgeSettlement(settlement, true)
                continue
            }
        }
    }

    fun purgeSettlement(settlement: Settlement, sendMessage: Boolean) {
        val settlementId = settlement._id

        if (Settlement.isCapital(settlementId)) {
            val nationId = Settlement.findPropById(settlementId, Settlement::nationId)

            if (nationId != null) {
                purgeNation(nationId, sendMessage)
            }
        }

        Settlement.delete(settlementId)

        if (sendMessage) {
            Notify all "&cSettlement ${settlement.name} on ${settlement.worldName} " +
                    "was purged for ${NATIONS_BALANCE.settlement.inactivityDays}+ days of complete inactivity."
        }
    }

    fun chargeSettlementUpkeep(settlement: Settlement) {
        val chunkCount = settlement.chunks.size
        val cost = (chunkCount - 10).coerceAtLeast(0) * NATIONS_BALANCE.settlement.chunkUpkeepCost

        if (settlement.balance >= cost) {
            Settlement.withdraw(settlement._id, cost)
            return
        }

        val centerX = settlement.chunks.map { chunkKeyX(it) }.average().roundToInt()
        val centerZ = settlement.chunks.map { chunkKeyZ(it) }.average().roundToInt()
        val sortedChunks = settlement.chunks.sortedByDescending {
            distanceSquared(chunkKeyX(it), 0, chunkKeyZ(it), centerX, 0, centerZ)
        }

        if (chunkCount < 10) {
            purgeSettlement(settlement, false)
            Notify all "&cSettlement ${settlement.name} on ${settlement.worldName} " +
                    "was purged for failing to pay upkeep."
            return
        }

        val toUnclaim = sortedChunks.subList(0, (sortedChunks.size * 0.1).toInt()).toSet()
        Settlement.unclaim(settlement._id, toUnclaim)

        Notify all "&cSettlement ${settlement.name} on ${settlement.worldName} " +
                "lost ${toUnclaim.size} chunks for failing to pay upkeep."
    }

    fun chargeOutpostUpkeep(outpost: NationOutpost) {
        val chunkCount = (Math.PI * outpost.radius.squared() / 16.squared()).toInt()
        val cost = NATIONS_BALANCE.nation.outpostChunkUpkeepCost * chunkCount
        val nation = Nation.findById(outpost.nationId) ?: return

        if (nation.balance >= cost) {
            Nation.withdraw(nation._id, cost)
            return
        }

        if (chunkCount < 10) {
            purgeOutpost(outpost._id, true)
            return
        }

        NationOutpost.setRadius(outpost._id, ceil(outpost.radius * 0.9).toInt())

        val difference = (0.1 * chunkCount).toInt()
        Notify all "&coutpost ${outpost.name} on ${outpost.worldName} " +
                "lost ~$difference chunks for failing to pay upkeep."
    }

    fun purgeNation(nationId: Oid<Nation>, sendMessage: Boolean) {
        val nation = Nation.findById(nationId) ?: return

        Nation.delete(nationId)

        if (sendMessage) {
            Notify all "&cNation ${nation.name} had its capital settlement purge and was purged itself!"
        }
    }

    fun purgeOutpost(outpostId: Oid<NationOutpost>, sendMessage: Boolean) {
        val outpost = NationOutpost.findById(outpostId) ?: return

        NationOutpost.delete(outpostId)

        if (sendMessage) {
            Notify all "&cOutpost ${outpost.name} on ${outpost.worldName} " +
                    "was purged for failing to pay upkeep."
        }

    }

    fun executeMoneyTasks() {
        doActivityCredits()

        doUpkeep()

        doZoneRent()
    }

    private fun doUpkeep() {
        for (settlement in SettlementCache.getAll()) {
            chargeSettlementUpkeep(settlement)
        }

        for (outpost in NationOutpostCache.getAll()) {
            chargeOutpostUpkeep(outpost)
        }
    }

    private fun doActivityCredits() {
        for (nationId: Oid<Nation> in Nation.allIds()) {
            val nation: Nation = NationCache[nationId]

            // Give the nation its station income if it has stations
            val stationCount = Territory.count(Territory::nationId eq nationId)
            val stationIncome = (stationCount * NATIONS_BALANCE.capture.hourlyIncome).toInt()

            if (stationIncome > 0) {
                Nation.deposit(nationId, stationIncome)
                Notify.nation(
                        nationId, "&6Your nation received &e${stationIncome.toCreditsString()}&6 credits " +
                        "from captured space station hourly income  with &3$stationCount&6 stations"
                )
            }

            val activeCount = SLPlayer.count(
                    and(SLPlayer::lastSeen gte ACTIVE_AFTER_TIME, SLPlayer::nationId eq nationId)
            ).toInt()
            val activityCredits = activeCount * NATIONS_BALANCE.nation.hourlyActivityCredits

            if (activityCredits > 0) {
                Nation.deposit(nationId, activityCredits)

                val leader = SettlementCache[nation.capitalId].leaderId
                Notify.player(
                        leader.uuid, "&2Your nation received &6${activityCredits.toCreditsString()}&2 " +
                        "for activity credits from &a$activeCount&2 active members"
                )
            }
        }

        for (settlement: Settlement in SettlementCache.getAll()) {
            val settlementId = settlement._id

            val activeCount = SLPlayer.count(
                    and(SLPlayer::lastSeen gte ACTIVE_AFTER_TIME, SLPlayer::settlementId eq settlementId)
            ).toInt()
            val activityCredits = activeCount * NATIONS_BALANCE.settlement.hourlyActivityCredits

            if (activityCredits > 0) {
                Settlement.deposit(settlementId, activityCredits)
                Notify.player(
                        settlement.leaderId.uuid, "&3Your settlement received &6${activityCredits.toCreditsString()}&3 " +
                        "for activity credits from &a$activeCount&3 active members"
                )
            }
        }
    }


    private fun doZoneRent() {
        for (zone in Regions.getAllOf<RegionSettlementZone>()) {
            val owner: SLPlayerId = zone.owner ?: continue
            val rent: Int = zone.cachedRent ?: continue

            val offlinePlayer = Bukkit.getOfflinePlayer(owner.uuid)

            if (!VAULT_ECO.has(offlinePlayer, rent.toDouble())) {
                Notify.settlement(zone.settlementId, "&c${offlinePlayer.name} failed to pay rent for zone ${zone.name}")
                continue
            }

            VAULT_ECO.withdrawPlayer(offlinePlayer, rent.toDouble())
            Settlement.deposit(zone.settlementId, rent)

            Notify.player(owner.uuid, "Paid ${rent.toCreditsString()} rent for zone ${zone.dbObjectId}")
        }
    }
}
