package net.starlegacy.feature.nations

import net.md_5.bungee.api.ChatColor.GOLD
import net.starlegacy.SETTINGS
import net.starlegacy.SLComponent
import net.starlegacy.cache.nations.TerritoryCache
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Territory
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.uuid
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionTerritory
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import java.lang.System.currentTimeMillis
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

object Sieges : SLComponent() {
    data class Siege(
        val siegerId: SLPlayerId,
        val period: Territory.SiegePeriod, // store period in siege in case owner changes period during siege
        val territoryId: Oid<Territory>,
        val bastionName: String,
        val start: Long
    )

    private val sieges = ArrayList<Siege>()

    private val siegeIntervalMillis get() = TimeUnit.DAYS.toMillis(NATIONS_BALANCE.capture.siegeIntervalDays)

    private fun dayOfWeek() = ZonedDateTime.now(ZoneId.of(SETTINGS.timezone)).dayOfWeek

    private fun siegePeriod() = when (ZonedDateTime.now(ZoneId.of(SETTINGS.timezone)).hour) {
        in 12..14 -> Territory.SiegePeriod.PERIOD_1
        in 15..17 -> Territory.SiegePeriod.PERIOD_2
        in 18..20 -> Territory.SiegePeriod.PERIOD_3
        else -> null
    }

    override fun onEnable() {
        Tasks.syncRepeat(20, 20) {
            updateSieges()
        }
    }

    private fun updateSieges() {
        for (siege: Siege in getSieges()) {
            val player: Player? = Bukkit.getPlayer(siege.siegerId.uuid)
            val elapsed = currentTimeMillis() - siege.start

            val territoryId = siege.territoryId

            val territory = TerritoryCache[territoryId]
            val bastion = territory.bastions.first { it.name == siege.bastionName }

            val captureRegion: RegionTerritory = Regions[territoryId]

            if (player == null
                || !captureRegion.contains(player.location)
                || !siegePeriodMatches(siege.period)
            ) {
                endSiege(siege)
                return
            }

            if (isVictoryConditionMet(player, territory, bastion)) {
                capture(player, territory, bastion)
                return
            }

            val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
            val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
            player.sendActionBar("$elapsedMinutes minutes, $elapsedSeconds seconds elapsed")
        }
    }

    private fun isVictoryConditionMet(player: Player, territory: Territory, bastion: Territory.Bastion): Boolean {
        val playerWorldName = player.world.name
        val playerPos = Vec3i(player.location)
        val bastionPos = Vec3i(bastion.x, bastion.y, bastion.z)
        return playerWorldName == territory.worldName && playerPos == bastionPos
    }

    override fun onDisable() {
        sieges.forEach(this::endSiege)
    }

    @Synchronized
    private fun locked(block: () -> Unit) = block()

    private fun asyncLocked(block: () -> Unit) = Tasks.async {
        locked(block)
    }

    private fun tryEndSiege(player: Player) = asyncLocked {
        val slPlayerId = player.slPlayerId
        sieges.find { it.siegerId == slPlayerId }?.let(this::endSiege)
    }

    private fun endSiege(siege: Siege) = asyncLocked {
        sieges.remove(siege)

        val playerName = SLPlayer.getName(siege.siegerId) ?: "UNKNOWN"
        val captureName = Territory.findPropById(siege.territoryId, Territory::name) ?: "??NULL??"

        Notify.online("${GOLD}Siege of $captureName by $playerName has failed!")
        Notify.discord("Siege of **$captureName** by **$playerName** has failed!")
    }

    fun beginSiege(player: Player, territory: Territory, bastion: Territory.Bastion) = asyncLocked {
        val playerNationId: Oid<Nation> = PlayerCache[player].nationId
            ?: return@asyncLocked player msg "&cYou need to be in a nation to siege a capture."

        val captureRegion = Regions.findFirstOf<RegionTerritory>(player.location)
            ?: return@asyncLocked player msg "&cYou must be within a capture's area to siege it"

        val captureId = captureRegion.dbObjectId
        val captureNationId = territory.nationId

        val bastionName = bastion.name
        val bastionNationId = bastion.occupierId

        val playerId = player.uniqueId.slPlayerId

        val isrecapture = playerNationId == captureNationId
        val isAttack = !isrecapture

        val lastAttempt = if (isrecapture) territory.recaptureTimestamp else territory.attackTimestamp
        val lastAttemptMillis = lastAttempt?.toInstant()?.toEpochMilli()

        val siegeSchedule = territory.siegeSchedule
        val dayOfWeek = siegeSchedule.dayOfWeek
        val period = siegeSchedule.period

        when {
            // Disallow sieging bastions already owned by the player's nation
            playerNationId == bastionNationId -> {
                return@asyncLocked player msg "&cYour nation already controls this bastion."
            }
            // Disallow sieging ally bastions except for recaptures
            isAttack && bastionNationId != null
                    && RelationCache[bastionNationId, playerNationId] >= NationRelation.Level.ALLY -> {
                return@asyncLocked player msg "&cThis bastion is owned by an ally of your nation"
            }
            // Disallow sieging bastions already under siege
            isUnderSiege(captureId, bastionName) -> {
                return@asyncLocked player msg "&cThis bastion is already under siege."
            }
            // Disallow sieging territories outside of their siege period
            !siegePeriodMatches(period) -> {
                return@asyncLocked player msg "&cThis territory can only be sieged" +
                        " in period $period" +
                        " (${period.text} Eastern time), but the current period is ${siegePeriod()}"
            }
            // Disallow attacking (but not recapturing) outside of the siege schedule's day of the week
            isAttack && !dayOfWeekMatches(dayOfWeek) -> {
                return@asyncLocked player msg "&cThis territory can only be attached" +
                        "on the day $dayOfWeek"
            }
            // Disallow participating in multiple sieges
            sieges.any { it.siegerId == playerId } -> {
                return@asyncLocked player msg "&cYou are already sieging a capture"
            }
            // Disallow sieging within the siege interval
            lastAttemptMillis != null && currentTimeMillis() - lastAttemptMillis < siegeIntervalMillis -> {
                return@asyncLocked player msg "This station was last sieged on $lastAttempt and cannot be sieged" +
                        " until ${NATIONS_BALANCE.capture.siegeIntervalDays} days after that."
            }
        }

        if (!VAULT_ECO.has(player, NATIONS_BALANCE.capture.siegeCost.toDouble())) {
            player msg "&cYou need C${NATIONS_BALANCE.capture.siegeCost} to begin a siege."
            return@asyncLocked
        } else {
            VAULT_ECO.withdrawPlayer(player, NATIONS_BALANCE.capture.siegeCost.toDouble())
        }

        if (captureNationId == null) {
            capture(player, territory, bastion)
            return@asyncLocked
        }

        if (isAttack) territory.attackTimestamp = Date()
        else territory.recaptureTimestamp = Date()

        Territory.save(territory)

        sieges.add(Siege(playerId, period, captureId, bastion.name, currentTimeMillis()))

        val nationName = NationCache[playerNationId].name
        val oldNationName = NationCache[captureNationId].name

        Notify.online(
            "$GOLD${player.name} of $nationName began a siege" +
                    " on bastion ${bastion.name}" +
                    " of territory ${territory.name}!" +
                    " (Current Nation: $oldNationName)"
        )
        Notify.discord(
            "**${player.name}** of $nationName began a siege" +
                    " on bastion ${bastion.name}" +
                    " of territory ${territory.name}!" +
                    " (Current Nation: $oldNationName)"
        )
    }

    private fun dayOfWeekMatches(dayOfWeek: DayOfWeek): Boolean {
//        return dayOfWeek == dayOfWeek()
        return true
    }

    private fun siegePeriodMatches(period: Territory.SiegePeriod): Boolean {
//        return siegePeriod() == territory.siegeSchedule.period
        return true
    }

    fun isUnderSiege(territoryId: Oid<Territory>): Boolean {
        return sieges.any { it.territoryId == territoryId }
    }

    fun isUnderSiege(territoryId: Oid<Territory>, bastionName: String): Boolean {
        return sieges.any { it.territoryId == territoryId && it.bastionName == bastionName }
    }

    private fun getSieges(): Iterable<Siege> = sieges

    fun capture(player: Player, territory: Territory, bastion: Territory.Bastion) {
        val captureRegion: RegionTerritory = Regions[territory._id]

        val world: World = checkNotNull(Bukkit.getWorld(territory.worldName))
        val oldNation = bastion.occupierId

        val playerNation = PlayerCache[player].nationId

        if (playerNation == null) {
            player msg "&cYou need to be in a nation to siege a capture."
            return
        }

        asyncLocked {
            val slPlayerId = player.slPlayerId

            sieges.removeIf { it.siegerId == slPlayerId }

            if (territory.nationId == playerNation) {
                player msg "&cThis territory is already captured by your nation, capture failed."
                return@asyncLocked
            }

            val nationName = NationCache[playerNation].name
            val oldNationName = oldNation?.let { NationCache[it].name } ?: "None"
            val playerName = player.name
            val numberBastions = territory.bastions.count { it.occupierId == playerNation } + 1

            Notify online "${GOLD}Bastion ${bastion.name} of territory ${territory.name}" +
                    " has been captured by $playerName of $nationName from $oldNationName." +
                    " $nationName now has $numberBastions of ${territory.bastions.size} bastions!"
            Notify discord "Bastion **${bastion.name}** of territory **${territory.name}**" +
                    " has been captured by **$playerName of $nationName** from **$oldNationName**"

            bastion.occupierId = playerNation

            if (territory.nationId != playerNation && territory.bastions.all { it.occupierId == playerNation }) {
                Notify online "${GOLD}Territory ${territory.name} has been captured by $playerName of $nationName from $oldNationName."
                Notify discord "Territory **${territory.name}** has been captured by **$playerName of $nationName** from **$oldNationName**"

                territory.nationId = playerNation

                // Reset recapture timestamp, because it was for the previous nation,
                // but not the attack timestamp, so that it cannot be
                // immediately attacked after they win
                territory.recaptureTimestamp = null
            }

            Territory.save(territory)

            if (oldNation != null) {
                giveRewards(player, world, slPlayerId, playerNation, captureRegion)
            }
        }
    }

    private fun giveRewards(
        player: Player,
        world: World,
        slPlayerId: SLPlayerId,
        playerNationId: Oid<Nation>,
        captureRegion: RegionTerritory
    ) {
        val reward = NATIONS_BALANCE.capture.siegerReward

        VAULT_ECO.depositPlayer(player, reward)

        player msg "&6Received &e${reward.toCreditsString()}"

        Tasks.sync {
            for (otherPlayer in world.players) {
                if (otherPlayer.slPlayerId == slPlayerId) {
                    continue
                }

                val otherPlayerNationId: Oid<Nation> = PlayerCache[otherPlayer].nationId
                    ?: continue

                if (RelationCache[otherPlayerNationId, playerNationId] < NationRelation.Level.ALLY) {
                    continue
                }

                if (!captureRegion.contains(otherPlayer.location)) {
                    continue
                }

                VAULT_ECO.depositPlayer(otherPlayer, reward)
                otherPlayer msg "&6Received &eK${reward.toCreditsString()}"
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        tryEndSiege(event.player)
    }
}
