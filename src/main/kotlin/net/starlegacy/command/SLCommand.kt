package net.starlegacy.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandHelp
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.HelpCommand
import net.md_5.bungee.api.ChatColor
import net.starlegacy.PLUGIN
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.RelationCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.*
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.uuid
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.command.CommandException
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.litote.kmongo.and
import org.litote.kmongo.contains
import org.litote.kmongo.eq
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class SLCommand : BaseCommand() {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    protected val plugin get() = PLUGIN

    companion object {
        val ASYNC_COMMAND_THREAD: ExecutorService = Executors.newSingleThreadExecutor(
            Tasks.namedThreadFactory("sl-async-commands")
        )
    }

    /**
     * Run this block of code async. Also, no two blocks passed to this method will run at the same time,
     * because it runs them all on a single thread. This prevents exploits from multiple people running
     * one command at the same time or in the same place or in rapid succession.
     *
     * HOWEVER, this does not help when people are on different servers, which should be kept in mind.
     */
    protected open fun asyncCommand(sender: CommandSender, block: () -> Unit) {
        ASYNC_COMMAND_THREAD.submit {
            try {
                block()
            } catch (e: Exception) {
                if (e is CommandException || e is InvalidCommandArgument) {
                    sender.sendMessage("${ChatColor.RED}Error: ${e.message}")
                    return@submit
                }

                val cause = e.cause
                if (cause is CommandException || cause is InvalidCommandArgument) {
                    sender.sendMessage("${ChatColor.RED}Error: ${cause.message}")
                    return@submit
                }

                log.error("Command Error for ${sender.name}", e)
                sender.sendMessage("${ChatColor.DARK_RED}Something went wrong with that command, please tell staff")
            }
        }
    }

    //region Utilities
    /**
     * Returns the UUID linked to the given name from the SLPlayers table if available,
     * else throws InvalidCommandArgument
     *
     * @throws InvalidCommandArgument if no UUID could be found for that name
     */
    protected fun resolveOfflinePlayer(name: String): UUID = SLPlayer.findIdByName(name)?.uuid
        ?: fail { "Player $name not found. Have they joined the server?" }
    //endregion


    @HelpCommand
    fun onHelp(@Suppress("UNUSED_PARAMETER") sender: CommandSender, help: CommandHelp) {
        help.showHelp()
    }

    protected val linesPerPage = 8

    protected fun getPlayerName(id: SLPlayerId): String {
        return Bukkit.getPlayer(id.uuid)?.name ?: SLPlayer.getName(id) ?: error("No such player $id")
    }

    protected fun getSettlementName(id: Oid<Settlement>): String {
        return SettlementCache[id].name
    }

    protected fun getNationName(id: Oid<Nation>): String {
        return NationCache[id].name
    }

    protected fun getSettlementTag(id: SLPlayerId, name: String, color: SLTextStyle = SLTextStyle.RESET): String {
        val tag = SettlementRole.getTag(id)?.plus(" ") ?: ""
        return "$tag$color$name"
    }

    protected fun getNationTag(id: SLPlayerId, name: String, color: SLTextStyle = SLTextStyle.RESET): String {
        val tag = NationRole.getTag(id)?.plus(" ") ?: ""
        return "$tag$color$name"
    }

    protected fun getRelation(sender: CommandSender, nation: Nation): NationRelation.Level = when (sender) {
        is Player -> PlayerCache[sender].nationId?.let { RelationCache[it, nation._id] }
        else -> null
    } ?: NationRelation.Level.NONE

    protected fun sendBreak(sender: CommandSender, color: SLTextStyle) = sender msg lineBreak(color)

    protected fun lineBreak(color: SLTextStyle = SLTextStyle.DARK_GRAY): String =
        "$color============================================="

    protected fun failIf(boolean: Boolean, message: () -> String) {
        if (boolean) {
            fail(message)
        }
    }

    protected fun fail(message: () -> String): Nothing {
        throw InvalidCommandArgument(message.invoke())
    }

    protected fun <T> T.fail(message: (T) -> String) {
        throw InvalidCommandArgument(message.invoke(this))
    }

    protected fun resolveSettlement(name: String): Settlement = SettlementCache.getByName(name)
        ?: fail { "Settlement $name not found" }

    protected fun resolveNation(name: String): Nation = NationCache.getByName(name)
        ?: fail { "Nation $name not found" }

    protected fun requireSettlementIn(sender: Player): Settlement {
        val settlementId = (PlayerCache[sender].settlementId
            ?: fail { "You need to be in a settlement to do that" })

        return SettlementCache[settlementId]
    }

    protected fun requireNationIn(sender: Player): Nation {
        val nationId = (PlayerCache[sender].nationId
            ?: fail { "You need to be in a nation to do that" })

        return NationCache[nationId]
    }

    protected fun isSettlementLeader(player: Player, settlement: Settlement): Boolean =
        settlement.leaderId == player.slPlayerId

    protected fun isNationLeader(player: Player, nation: Nation): Boolean =
        SettlementCache[NationCache[nation._id].capitalId].leaderId == player.slPlayerId

    protected fun requireSettlementLeader(sender: Player, settlement: Settlement) =
        failIf(!isSettlementLeader(sender, settlement))
        { "Only the settlement leader can do that" }

    protected fun requireNationLeader(sender: Player, nation: Nation) =
        failIf(!isNationLeader(sender, nation))
        { "Only the nation leader can do that" }

    protected fun requireIsMemberOf(slPlayerId: SLPlayerId, settlement: Settlement, name: String? = null) {
        failIf(!SLPlayer.isMemberOfSettlement(slPlayerId, settlement._id))
        { "${name ?: "That player"} is not a member of the settlement" }
    }

    protected fun requireNotSettlementLeader(sender: Player, settlement: Settlement) {
        failIf(isSettlementLeader(sender, settlement)) {
            "You can't do that while the leader of a settlement! " +
                    "Hint: To disband a settlement, use /s disband, " +
                    "or to change the leader, use /s set leader"
        }
    }

    protected fun requireNotInSettlement(sender: Player) =
        failIf(PlayerCache[sender].settlementId != null)
        { "You can't do that while in a settlement" }

    protected fun requireNotInNation(sender: Player) =
        failIf(PlayerCache[sender].nationId != null)
        { "You can't do that while in a nation. Hint: To leave the nation, use /n leave" }

    protected fun requireNotCapital(settlement: Settlement, action: String = "do that") =
        failIf(settlement.nationId?.let(NationCache::get)?.capitalId == settlement._id)
        { "The capital settlement can't $action!" }

    protected fun requireMoney(sender: Player, amount: Number, text: String = "do that") {
        failIf(!VAULT_ECO.has(sender, amount.toDouble())) {
            "You don't have enough money to $text! It requires ${amount.toCreditsString()}, " +
                    "but you only have ${VAULT_ECO.getBalance(sender).toCreditsString()}"
        }
    }

    protected fun requireSettlementPermission(
        sender: Player, settlement: Settlement, permission: SettlementRole.Permission
    ) {
        if (isSettlementLeader(sender, settlement)) {
            return // leaders have all perms
        }

        val query = and(
            SettlementRole::parent eq settlement._id, // just in case, but should never have a role from another settlement
            SettlementRole::members contains sender.slPlayerId,
            SettlementRole::permissions contains permission
        )

        failIf(SettlementRole.none(query)) { "You need the settlement permission $permission to do that" }
    }

    protected fun requireNationPermission(
        sender: Player, nation: Nation, permission: NationRole.Permission
    ) {
        if (isNationLeader(sender, nation)) {
            return // leaders have all perms
        }

        val query = and(
            NationRole::parent eq nation._id, // just in case, but should never have a role from another nation
            NationRole::members contains sender.slPlayerId,
            NationRole::permissions contains permission
        )

        failIf(NationRole.none(query)) { "You need the nation permission $permission to do that" }
    }

    protected fun getStarshipRiding(sender: Player) = ActiveStarships.findByPassenger(sender)
        ?: fail { "You must be riding a starship" }

    protected fun getStarshipPiloting(sender: Player) = ActiveStarships.findByPilot(sender)
        ?: fail { "You must be piloting a starship" }

    protected fun validateNameString(name: String) {
        failIf (!name.isAlphanumeric()) { "Name must be alphanumeric" }

        failIf (name.length < 3) { "Name cannot be less than 3 characters" }

        failIf (name.length > 20) { "Name cannot be more than 40 characters" }
    }

    open fun supportsVanilla(): Boolean = false
}
