package net.starlegacy.command.nations

import net.starlegacy.command.SLCommand
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.*
import net.starlegacy.database.uuid
import net.starlegacy.feature.nations.NATIONS_BALANCE
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.utils.*
import co.aikar.commands.annotation.*
import co.aikar.commands.annotation.Optional
import net.md_5.bungee.api.chat.TextComponent
import net.starlegacy.cache.nations.*
import net.starlegacy.feature.nations.region.types.RegionNationOutpost
import net.starlegacy.util.*
import org.bukkit.Color
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.eq
import org.litote.kmongo.ne
import java.util.*
import kotlin.math.max
import kotlin.math.min

@CommandAlias("nation|n")
internal object NationCommand : SLCommand() {
    private fun validateName(name: String, nation: Nation?) {
        validateNameString(name)

        val existingNation: Nation? = NationCache.getByName(name)
        failIf(existingNation != null && (nation == null || nation._id != existingNation._id)) {
            "A nation named $name already exists."
        }
    }

    private fun validateColor(red: Int, green: Int, blue: Int, nation: Nation?): Color {
        failIf(sequenceOf(red, green, blue).any { it !in 0..255 })
        { "Red, green, and blue must be integers within 0-255" }

        val color = Color.fromRGB(red, green, blue)

        val query = if (nation == null) EMPTY_BSON else Nation::_id ne nation._id

        for (results in Nation.findProps(query, Nation::name, Nation::color)) {
            val nationName = results[Nation::name]
            val nationColor = Color.fromRGB(results[Nation::color])

            val r1 = color.red.toDouble()
            val g1 = color.green.toDouble()
            val b1 = color.blue.toDouble()
            val r2 = nationColor.red.toDouble()
            val g2 = nationColor.green.toDouble()
            val b2 = nationColor.blue.toDouble()
            val distance = distance(r1, g1, b1, r2, g2, b2)

            failIf(distance < 10)
            { "That color is too similar to the color of the nation $nationName! Distance: $distance" }

            log.info("Distance from $nationName: $distance")
        }

        return color
    }

    @Subcommand("create")
    @CommandCompletion("@nothing @range:1-255 @range:0-255 @range:0-255")
    @Description("Create a nation. Color values must be R-G-B color values, each from 0-255")
    fun onCreate(
        sender: Player, name: String, red: Int, green: Int, blue: Int, @Optional cost: Int?
    ) = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)
        requireNotInNation(sender)
        validateName(name, null)
        val color = validateColor(red, green, blue, nation = null)

        val realCost = NATIONS_BALANCE.nation.createCost
        requireMoney(sender, realCost, "create a nation")

        failIf(cost != realCost) {
            "You must acknowledge the cost of creating a nation to create one. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/nation create $name $red $green $blue $realCost"
        }

        Nation.create(name, settlement._id, color.asRGB())
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())

        Notify all "&e${sender.name}, leader of the settlement ${settlement.name}, founded the nation $name!"
    }

    @Subcommand("disband")
    @Description("Disband your nation (this cannot be undone!)")
    fun onDisband(sender: Player, @Optional name: String?) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationLeader(sender, nation)

        failIf(name != nation.name)
        { "To disband your nation, you must confirm by specifying the name. Run the command: /n disband ${nation.name}" }

        Nation.delete(nation._id)

        Notify all "&eThe nation ${nation.name} has been disbanded by its leader ${sender.name}!"
    }

    @Subcommand("invite")
    @CommandCompletion("@settlements")
    @Description("Invite a settlement to your nation")
    fun onInvite(sender: Player, settlementName: String) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationPermission(sender, nation, NationRole.Permission.SETTLEMENT_INVITE)

        val settlement = resolveSettlement(settlementName)
        failIf(settlement.nationId == nation._id)
        { "$settlement is already in your nation" }

        val leaderId = settlement.leaderId.uuid

        val nationName = nation.name

        if (!Nation.isInvited(nation._id, settlement._id)) {
            Nation.addInvite(nation._id, settlement._id)
            sender msg "&aInvited settlement ${settlement.name} to your nation"
            Notify.player(
                player = leaderId,
                message = "&bYour settlement is invited to the nation $nationName by ${sender.name}! " +
                        "To accept, use &e&o/nation join $nationName"
            )
        } else {
            Nation.removeInvite(nation._id, settlement._id)
            sender msg "&eCancelled invite for settlement $settlement to your nation"
            Notify.player(
                player = leaderId,
                message = "&eYour settlement's invite to the nation $nationName has been revoked by ${sender.name}"
            )
        }
    }

    @Subcommand("invites")
    fun onInvites(sender: Player) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationPermission(sender, nation, NationRole.Permission.SETTLEMENT_INVITE)

        val invitedSettlements = Nation.findPropById(nation._id, Nation::invites)
        sender msg "&7Invited Settlements:&b ${invitedSettlements?.joinToString { SettlementCache[it].name }}"
    }

    @Subcommand("join")
    @CommandCompletion("@nations")
    @Description("Join a nation which you're invited to")
    fun onJoin(sender: Player, nationName: String) = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)
        requireNotInNation(sender)
        val nation: Nation = resolveNation(nationName)

        val settlementName = settlement.name

        failIf(!Nation.isInvited(nation._id, settlement._id))
        { "$settlementName isn't invited to ${nation.name}" }

        Nation.removeInvite(nation._id, settlement._id)
        Settlement.joinNation(settlement._id, nation._id)

        Notify all "&dSettlement &b$settlementName&d joined the nation &c${nation.name}&d!"
    }

    @Subcommand("leave")
    @Description("Leave the nation you're in")
    fun onLeave(sender: Player, @Optional nationName: String?) = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)
        val nation = requireNationIn(sender)
        requireNotCapital(settlement, action = "leave the nation")

        failIf(nation.name != nationName)
        { "You need to confirm using the name of the nation. Run the command: /n leave ${nation.name}" }

        Settlement.leaveNation(settlement._id)

        Notify all "&eSettlement &b${settlement.name}&e seceded from the nation &c${nation.name}&e!"
    }

    @Subcommand("kick")
    @Description("Kick a settlement from your nation")
    @CommandCompletion("@member_settlements")
    fun onKick(sender: Player, settlementName: String) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationPermission(sender, nation, NationRole.Permission.SETTLEMENT_KICK)

        val settlement = resolveSettlement(settlementName)

        failIf(settlement.nationId != nation._id)
        { "Settlement $settlementName is not in your nation" }

        requireNotCapital(settlement, action = "be kicked")

        Settlement.leaveNation(settlement._id)

        Notify all "&6${sender.name}&e kicked settlement $settlementName from the nation ${nation.name}"
    }

    @Subcommand("set name")
    @Description("Rename your nation")
    fun onSetName(sender: Player, newName: String, @Optional cost: Int?) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationLeader(sender, nation)
        validateName(newName, nation)

        val oldName = nation.name
        failIf(oldName == newName)
        { "Your nation is already named $oldName" }

        val realCost = NATIONS_BALANCE.nation.renameCost
        requireMoney(sender, realCost, "rename")

        failIf(cost != realCost) {
            "You must acknowledge the cost of renaming to rename it. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/nation set name $newName $realCost"
        }

        Nation.setName(nation._id, newName)
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())

        Notify.online("&6${sender.name}&d renamed their nation &c$oldName&d to &a$newName&d!")
    }

    @Subcommand("set color")
    @Description("Change the color your nation")
    fun onSetColor(sender: Player, red: Int, green: Int, blue: Int) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationLeader(sender, nation)
        val color: Color = validateColor(red, green, blue, nation)

        Nation.setColor(nation._id, color.asRGB())

        sender msg "&aUpdated nation color."
    }

    @Subcommand("set capital")
    @CommandCompletion("@member_settlements")
    fun setCapital(sender: Player, newCapital: String) = asyncCommand(sender) {
        val nation = requireNationIn(sender)
        requireNationLeader(sender, nation)

        val settlement = resolveSettlement(newCapital)

        failIf(settlement._id == requireSettlementIn(sender)._id)
        { "Your settlement is already the capital" }

        failIf(settlement.nationId != nation._id)
        { "Settlement ${settlement.name} is not in your nation" }

        Nation.setCapital(nation._id, settlement._id)

        Notify all "&6${sender.name}&d changed the capital of their nation ${nation.name} to ${settlement.name}!"
    }

    @Subcommand("top|list")
    @Description("View the top nations on Star Legacy")
    fun onTop(sender: CommandSender, @Optional page: Int?): Unit = asyncCommand(sender) {
        val lines = LinkedList<TextComponent>()
        lines += lineBreak().fromLegacy()

        val nations = Nation.allIds()

        val nationMembers: Map<Oid<Nation>, List<SLPlayerId>> =
            nations.associateWith { Nation.getMembers(it).toList() }

        val lastSeenMap: Map<SLPlayerId, Date> = SLPlayer
            .findProps(SLPlayer::nationId ne null, SLPlayer::_id, SLPlayer::lastSeen)
            .associate { it[SLPlayer::_id] to it[SLPlayer::lastSeen] }

        val activeMemberCounts: Map<Oid<Nation>, Int> = nationMembers.mapValues { (_, members) ->
            members.count { lastSeenMap[it]?.let(::isActive) == true }
        }

        val semiActiveMemberCounts: Map<Oid<Nation>, Int> = nationMembers.mapValues { (_, members) ->
            members.count { lastSeenMap[it]?.let(::isSemiActive) == true }
        }

        val sortedNations: List<Oid<Nation>> = nations.toList()
            .sortedByDescending { nationMembers[it]?.size ?: 0 }
            .sortedByDescending { semiActiveMemberCounts[it] ?: 0 }
            .sortedByDescending { activeMemberCounts[it] ?: 0 }

        val pages = max(1, sortedNations.size / super.linesPerPage)
        val index = (max(min(page ?: 1, pages), 1)) - 1
        val nationsOnPage = sortedNations.subList(
            fromIndex = index * linesPerPage,
            toIndex = min(index * linesPerPage + linesPerPage, sortedNations.size)
        )

        val nameColor = SLTextStyle.GOLD
        val leaderColor = SLTextStyle.AQUA
        val membersColor = SLTextStyle.BLUE
        val activeColor = SLTextStyle.GREEN
        val semiActiveColor = SLTextStyle.GRAY
        val inactiveColor = SLTextStyle.RED
        val settlementsColor = SLTextStyle.DARK_AQUA
        val outpostsColor = SLTextStyle.YELLOW
        val split = "&8|"

        lines += ("${nameColor}Name " +
                "$split ${leaderColor}Leader " +
                "$split ${membersColor}Members " +
                "$split ${settlementsColor}Settlements " +
                "$split ${outpostsColor}Outposts").fromLegacy()

        for (nationId in nationsOnPage) {
            val nation = NationCache[nationId]

            val members = nationMembers[nationId]!!

            var active = 0
            var semiActive = 0
            var inactive = 0

            for (member in members) {
                val lastSeen = lastSeenMap[member]!!
                when {
                    isActive(lastSeen) -> active++
                    isSemiActive(lastSeen) -> semiActive++
                    isInactive(lastSeen) -> inactive++
                }
            }

            val line = TextComponent()

            val name = nation.name
            val leaderId = SettlementCache[nation.capitalId].leaderId
            val leaderName = SLPlayer.getName(leaderId) ?: "(???)"

            line.addExtra("    $name ".style(nameColor).cmd("/n info $name").hover("Click for more info"))
            line.addExtra(leaderName.style(leaderColor))
            line.addExtra(" ${members.count()}".style(membersColor))
            line.addExtra(darkGray(" ["))
            line.addExtra("$active ".style(activeColor))
            line.addExtra("$semiActive ".style(semiActiveColor))
            line.addExtra("$inactive ".style(inactiveColor))
            line.addExtra(darkGray("]"))
            line.addExtra(" ${SettlementCache.getAll().count { it.nationId == nationId }}".style(settlementsColor))
            line.addExtra((" " + NationOutpostCache.listByNationId(nationId).size.toString()).style(outpostsColor))

            lines += line
        }

        val pageLine = TextComponent()

        if (index > 0) {
            pageLine.addExtra(darkGreen(" ["))
            pageLine.addExtra(white("<--").cmd("/nation top $index").hover("Click to see previous page"))
            pageLine.addExtra(darkGreen("]"))
        }

        pageLine.addExtra(darkAqua(" Page "))
        pageLine.addExtra(gray("${index + 1}/$pages "))

        if (index < pages - 1) {
            pageLine.addExtra(darkGreen(" ["))
            pageLine.addExtra(white("-->").cmd("/nation top ${index + 2}").hover("Click to see next page"))
            pageLine.addExtra(darkGreen("]"))
        }

        lines += pageLine

        lines += lineBreak().fromLegacy()

        lines.forEach(sender::msg)
    }

    @Subcommand("info")
    @CommandCompletion("@nations")
    fun onInfo(sender: CommandSender, @Optional nationName: String?): Unit = asyncCommand(sender) {
        val nation = when (sender) {
            is Player -> {
                (nationName?.let(::resolveNation)
                    ?: PlayerCache[sender].nationId?.let(NationCache::get)
                    ?: fail { "You need to specify a nation. /n info <nation>" })
            }
            else -> {
                resolveNation(nationName ?: fail { "Non-players must specify a nation" })
            }
        }

        val lines = LinkedList<TextComponent>()
        lines += lineBreak().fromLegacy()

        lines += "                                 &6&b${nation.name}".fromLegacy()

        val relation: NationRelation.Level = getRelation(sender, nation)

        lines += "&5Relation: &r${relation.coloredName}".fromLegacy()

        val outposts: List<RegionNationOutpost> = Regions.getAllOf<RegionNationOutpost>()
            .filter { it.nationId == nation._id }

        if (outposts.isNotEmpty()) {
            lines += darkPurple("Outposts (${outposts.size}): ") + outposts.joinToText { outpost ->
                darkGreen(outpost.name).hover(outpost.toString())
            }
        }

        val territories: List<Territory> = TerritoryCache.getAll()
            .filter { it.nationId == nation._id }

        if (territories.isNotEmpty()) {
            lines += darkPurple("Territories (${territories.size}): ") + territories.joinToText { capture ->
                darkGreen(capture.name).hover(capture.toString())
            }
        }

        val settlementIds: List<Oid<Settlement>> = Nation.getSettlements(nation._id)
            .sortedByDescending { SLPlayer.count(SLPlayer::settlementId eq it) }
            .toList()
        if (settlementIds.isNotEmpty()) {
            lines += darkPurple("Settlements (${settlementIds.size}): ") + settlementIds.joinToText { settlementId ->
                val settlement = SettlementCache[settlementId]
                val settlementName = settlement.name
                val leaderName = getPlayerName(settlement.leaderId)
                val memberCount = SLPlayer.count(SLPlayer::settlementId eq settlementId)
                return@joinToText darkAqua(settlementName)
                    .hover("$settlementName led by $leaderName with $memberCount members")
                    .cmd("/s info $settlementName")
            }
        }

        lines += "&3Balance:&7 ${nation.balance}".fromLegacy()

        val leader = SettlementCache[nation.capitalId].leaderId
        lines += "&3Leader:&7 ${getNationTag(leader, getPlayerName(leader))}".fromLegacy()

        val activeStyle = SLTextStyle.GREEN
        val semiActiveStyle = SLTextStyle.GRAY
        val inactiveStyle = SLTextStyle.RED
        val members: List<Triple<SLPlayerId, String, Date>> = SLPlayer
            .findProps(SLPlayer::nationId eq nation._id, SLPlayer::lastKnownName, SLPlayer::lastSeen)
            .map { Triple(it[SLPlayer::_id], it[SLPlayer::lastKnownName], it[SLPlayer::lastSeen]) }
            .sortedByDescending { it.third }

        val names = LinkedList<String>()
        var active = 0
        var semiActive = 0
        var inactive = 0
        for ((playerId, name, lastSeen) in members) {
            val style: SLTextStyle = when {
                isActive(lastSeen) -> {
                    active++
                    activeStyle
                }
                isSemiActive(lastSeen) -> {
                    semiActive++
                    semiActiveStyle
                }
                isInactive(lastSeen) -> {
                    inactive++
                    inactiveStyle
                }
                else -> error("Impossible!")
            }
            names.add(getNationTag(playerId, name, style))
        }
        lines += "&3Members:&7 (${members.size}) &7(&a$active Active &7$semiActive Semi-Active &c$inactive Inactive&7)"
            .fromLegacy()

        val limit = 10
        lines += names.joinToString("&7, &r", limit = limit)
            .replace("${SLTextStyle.RESET}", "&7")
            .fromLegacy()

        if (names.size > limit) {
            lines += darkPurple("[Hover for full member list]").hover(names.joinToString("&7, &r").colorize())
        }

        lines += lineBreak().fromLegacy()

        lines.forEach(sender::msg)
    }

    @Subcommand("role")
    fun onRole(sender: CommandSender): Unit = fail { "Use /nrole, not /n role (remove the space)" }
}
