package net.starlegacy.command.nations

import co.aikar.commands.annotation.*
import co.aikar.commands.annotation.Optional
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.md_5.bungee.api.chat.TextComponent
import net.starlegacy.cache.nations.*
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.schema.nations.SettlementRole
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.database.schema.space.Star
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.nations.NATIONS_BALANCE
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.nations.region.types.RegionSettlementZone
import net.starlegacy.feature.nations.utils.*
import net.starlegacy.util.*
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.litote.kmongo.eq
import org.litote.kmongo.ne
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("unused")
@CommandAlias("settlement|s")
internal object SettlementCommand : SLCommand() {
    private fun validateName(name: String, settlementId: Oid<Settlement>?) {
        validateNameString(name)

        val existingSettlement: Settlement? = SettlementCache.getByName(name)
        failIf(existingSettlement != null && (settlementId == null || settlementId != existingSettlement._id)) {
            "A settlement named $name already exists."
        }
    }

    private fun validateChunkClaimable(settlement: Settlement?, world: World, chunkX: Int, chunkZ: Int) {
        checkSettlementConflicts(chunkX, chunkZ, world, settlement)
        checkNationOutpostConflicts(chunkX, chunkZ, world)
        checkCaptureConflicts(world, chunkX, chunkZ)
        checkWorldGuardConflicts(world, chunkX, chunkZ)
        checkStarConflicts(world, chunkX, chunkZ)
        checkPlanetOrbitConflicts(world, chunkX, chunkZ)
    }

    private fun checkSettlementConflicts(chunkX: Int, chunkZ: Int, world: World, settlement: Settlement?) {
        val chunkKey = chunkKey(chunkX, chunkZ)

        for (other in SettlementCache.listByWorld(world.name)) {
            failIf(other._id != settlement?._id && other.chunks.contains(chunkKey)) {
                "This chunk is already claimed by ${other.name}"
            }
        }
    }

    private fun checkNationOutpostConflicts(chunkX: Int, chunkZ: Int, world: World) {
        val chunkRectangle = chunkToRectangle2D(chunkX, chunkZ)

        for (outpost in NationOutpostCache.listByWorld(world.name)) {

            val ellipse = circleToEllipse2D(outpost.centerX, outpost.centerZ, outpost.radius)

            failIf(ellipse.intersects(chunkRectangle)) {
                "This chunk is already claimed"
            }
        }
    }

    private fun checkCaptureConflicts(world: World, chunkX: Int, chunkZ: Int) {
        val chunkRectangle = chunkToRectangle2D(chunkX, chunkZ)

        for (capture in TerritoryCache.getAll().filter { it.worldName == world.name }) {
            val ellipse = circleToEllipse2D(capture.x, capture.z, capture.radius)

            failIf(ellipse.intersects(chunkRectangle)) {
                "This chunk is too close to the capture ${capture.name}"
            }
        }
    }

    private fun checkWorldGuardConflicts(world: World, chunkX: Int, chunkZ: Int) {
        val regionManager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            ?: return

        val chunkRectangle = chunkToRectangle2D(chunkX, chunkZ)

        for ((regionName, region) in regionManager.regions.entries) {
            val minX = region.minimumPoint.blockX
            val minZ = region.minimumPoint.blockZ
            val width = region.maximumPoint.blockX - minX + 1
            val length = region.maximumPoint.blockZ - minZ + 1
            val regionRectangle = Rectangle2D.Double(minX.d(), minZ.d(), width.d(), length.d())

            failIf(chunkRectangle.intersects(regionRectangle)) {
                "This claim would intersect with the region $regionName"
            }
        }
    }

    private fun checkStarConflicts(world: World, chunkX: Int, chunkZ: Int) {
        val chunkRectangle = chunkToRectangle2D(chunkX, chunkZ)

        for (star: Star in StarCache.getAll().filter { it.instance.spaceWorld == world }) {
            val radius = star.classification.radius
            val ellipse = circleToEllipse2D(star.x, star.z, radius)

            failIf(ellipse.intersects(chunkRectangle)) {
                "This claim would be too close to the star ${star.name}"
            }
        }
    }

    private fun checkPlanetOrbitConflicts(world: World, chunkX: Int, chunkZ: Int) {
        val chunkRectangle = chunkToRectangle2D(chunkX, chunkZ)

        for (planet: Planet in PlanetCache.getAll().filter { it.instance.spaceWorld == world }) {
            val padding = 1000
            val location = planet.instance.location
            val radius = planet.atmosphereRadius

            val innerRadius = planet.orbitDistance - padding - radius
            val innerEllipse = circleToEllipse2D(location.x, location.z, innerRadius)

            val outerRadius = planet.orbitDistance + padding + radius
            val outerEllipse = circleToEllipse2D(location.x, location.z, outerRadius)

            failIf(outerEllipse.intersects(chunkRectangle) && !innerEllipse.contains(chunkRectangle)) {
                "This claim would be in the way of ${planet.name}'s orbit"
            }
        }
    }

    @Subcommand("create")
    @Description("Create your own settlement in the chunk you're in")
    fun onCreate(sender: Player, name: String, @Optional cost: Int?): Unit = asyncCommand(sender) {
        failIf(!sender.hasPermission("nations.settlement.create")) {
            "You can't create outposts here!"
        }

        requireNotInSettlement(sender)

        validateName(name, null)

        val chunk = sender.chunk
        validateChunkClaimable(null, chunk.world, chunk.x, chunk.z)

        // TODO: separate cost for civilized worlds
        val realCost = NATIONS_BALANCE.settlement.creationCost
        requireMoney(sender, realCost, "create a settlement")

        failIf(cost != realCost) {
            "You must acknowledge the cost of the settlement to create it. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/settlement create $name $realCost"
        }

        Settlement.create(chunk.world.name, setOf(chunk.chunkKey), name, sender.slPlayerId)
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())

        Notify all "&a${sender.name} has founded the settlement $name!"

        // No manual territory cache update is needed as settlement creation should automatically trigger that
    }

    @Subcommand("claim")
    @Description("Claim an area of chunks around you")
    fun onClaim(sender: Player, extraRadius: Int, @Optional cost: Int?): Unit = asyncCommand(sender) {
        failIf(extraRadius !in 0..8) { "Extra radius must be between 0 and 8 chunks" }

        val settlement = requireSettlementIn(sender)
        requireSettlementPermission(sender, settlement, SettlementRole.Permission.MANAGE_CHUNKS)

        val chunk = sender.chunk
        val world = chunk.world
        failIf(world.name != settlement.worldName) { "This area is in a different world" }

        val baseX = chunk.x
        val baseZ = chunk.z

        val chunkKeys = ArrayList<Long>((extraRadius * 2 + 1) * (extraRadius * 2 + 1))

        for (dx in -extraRadius..extraRadius) {
            for (dz in -extraRadius..extraRadius) {
                val x = baseX + dx
                val z = baseZ + dz
                val chunkKey = chunkKey(x, z)

                validateChunkClaimable(settlement, world, x, z)

                chunkKeys += chunkKey
            }
        }

        failIf(chunkKeys.all { new ->
            settlement.chunks.none { existing ->
                abs(chunkKeyX(existing) - chunkKeyX(new)) + abs(chunkKeyZ(existing) - chunkKeyZ(new)) == 1
            }
        }) { "This area is not adjacent to any of your settlement's chunks" }

        chunkKeys.removeAll(settlement.chunks)

        failIf(chunkKeys.isEmpty()) { "This area is already claimed by your settlement" }

        failIf(SettlementCache.listByWorld(settlement.worldName).any { it.chunks.intersect(chunkKeys).isNotEmpty() }) {
            "At least one chunk in this area is claimed by another settlement"
        }

        val realCost = NATIONS_BALANCE.settlement.chunkCost * chunkKeys.size
        requireMoney(sender, realCost, "claim ${chunkKeys.size} chunk(s)")

        failIf(cost != realCost) {
            "You must acknowledge the cost of the chunks to claim them. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/settlement claim $extraRadius $realCost"
        }

        Settlement.claim(settlement._id, chunkKeys.toSet())

        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())

        sender msg "&aClaimed ${chunkKeys.size} chunk(s) centered at $baseX, $baseZ for ${realCost.toCreditsString()}"
    }

    @Subcommand("unclaim")
    @Description("Unclaim the chunk area around you")
    fun onUnclaim(sender: Player, extraRadius: Int, @Optional count: Int?): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementPermission(sender, settlement, SettlementRole.Permission.MANAGE_CHUNKS)

        val chunk = sender.chunk
        val world = chunk.world
        failIf(world.name != settlement.worldName) { "This chunk is in a different world" }

        val baseX = chunk.x
        val baseZ = chunk.z

        val chunkKeys = ArrayList<Long>((extraRadius * 2 + 1) * (extraRadius * 2 + 1))

        for (dx in -extraRadius..extraRadius) {
            for (dz in -extraRadius..extraRadius) {
                val x = baseX + dx
                val z = baseZ + dz
                val chunkKey = chunkKey(x, z)

                validateChunkClaimable(settlement, world, x, z)

                val rectangle = Rectangle(x, z, 16, 16)
                val zones = Regions.get<RegionSettlement>(settlement._id).children
                    .filterIsInstance<RegionSettlementZone>()
                    .filter { zone ->
                        val (minX, _, minZ) = zone.minPoint
                        val (maxX, _, maxZ) = zone.maxPoint
                        rectangle.intersects(Rectangle(minX, minZ, maxX - minX + 1, maxZ - minZ + 1))
                    }

                failIf(zones.any()) { "The zone(s) ${zones.joinToString { it.name }} intersect with chunk $x $z" }

                chunkKeys += chunkKey
            }
        }

        chunkKeys.removeIf { !settlement.chunks.contains(it) }

        failIf(chunkKeys.isEmpty()) { "This area is not claimed by your settlement" }

        val realCount = chunkKeys.size

        failIf(settlement.chunks.size - realCount < 1) { "Cannot unclaim all remaining chunks" }

        failIf(count != realCount) {
            "To confirm that you want to unclaim $count chunk(s), /settlement unclaim $extraRadius $realCount"
        }

        // TODO: Handle unclaiming chunks with merchants in them

        Settlement.unclaim(settlement._id, chunkKeys.toSet())

        sender msg "&aUnclaimed $realCount chunk(s) centered at $baseX, $baseZ"
    }

    @Subcommand("disband|delete")
    @Description("Disband your settlement, permanently deleting it")
    fun onDisband(sender: Player, @Optional name: String?): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)

        requireSettlementLeader(sender, settlement)
        requireNotCapital(settlement)

        val settlementName = settlement.name

        failIf(settlementName != name)
        { "You must verify the name of your settlement to disband it. To disband your settlement, use /s disband $settlementName" }

        Settlement.delete(settlement._id)

        Notify all "&e${sender.name} has disbanded their settlement $settlementName!"

        // No manual territory cache update is needed as settlement removal from territory should automatically trigger that
        // Additionally, all members of the settlement should be updated as their player cache will be updated,
        // which triggers a territory access cache update for them as well.
    }

    @Subcommand("invite")
    @CommandCompletion("@players")
    @Description("Invite a player to your settlement so they can join")
    fun onInvite(sender: Player, player: String): Unit = asyncCommand(sender) {
        val playerId: UUID = resolveOfflinePlayer(player)
        val slPlayerId = playerId.slPlayerId

        val settlement: Settlement = requireSettlementIn(sender)

        requireSettlementPermission(sender, settlement, SettlementRole.Permission.INVITE)

        failIf(SLPlayer.matches(slPlayerId, SLPlayer::settlementId eq settlement._id))
        { "$player is already in your settlement!" }

        val settlementName = settlement.name

        if (Settlement.isInvitedTo(settlement._id, slPlayerId)) {
            Settlement.removeInvite(settlement._id, slPlayerId)
            sender msg "&bRemoved $player's invite to your settlement."
            Notify.player(playerId, "&eYou were un-invited from $settlementName by ${sender.name}")
        } else {
            Settlement.addInvite(settlement._id, slPlayerId)
            sender msg "&bInvited $player to your settlement."
            Notify.player(
                playerId, "&bYou were invited to $settlementName by ${sender.name}. " +
                        "To join, use &o/s join $settlementName"
            )
        }
    }

    @Subcommand("invites")
    fun onInvites(sender: Player) = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementPermission(sender, settlement, SettlementRole.Permission.INVITE)

        val invitedPlayers = Settlement.findPropById(settlement._id, Settlement::invites)
        sender msg "&7Invited Settlements:&b ${invitedPlayers?.joinToString { getPlayerName(it) }}"
    }

    @Subcommand("join")
    @CommandCompletion("@settlements")
    @Description("Join the settlement, requires an invite")
    fun onJoin(sender: Player, settlementName: String): Unit = asyncCommand(sender) {
        requireNotInSettlement(sender)

        val settlement: Settlement = resolveSettlement(settlementName)

        failIf(!Settlement.isInvitedTo(settlement._id, sender.slPlayerId))
        { "You're not invited the settlement ${settlement.name}!" }

        SLPlayer.joinSettlement(sender.slPlayerId, settlement._id)

        Notify.online("&a${sender.name} joined the settlement ${settlement.name}!")

        // No manual territory cache updating is needed, as the player is added to the settlement/nation, thus
        // automatically triggering the player cache update, which triggers the territory cache update
    }

    @Subcommand("leave|quit")
    @Description("Leave the settlement you're in")
    fun onLeave(sender: Player): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        val settlementName = settlement.name

        requireNotSettlementLeader(sender, settlement)

        SLPlayer.leaveSettlement(sender.slPlayerId)

        Notify.online("&e${sender.name} left the settlement $settlementName!")

        // No manual territory cache updating is needed, as the player is removed from the settlement/nation, thus
        // automatically triggering the player cache update, which triggers the territory cache update
    }

    @Subcommand("kick")
    @CommandCompletion("@players")
    @Description("Kick a player from your settlement, forcing them to leave")
    fun onKick(sender: Player, player: String): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementPermission(sender, settlement, SettlementRole.Permission.KICK)
        val settlementName = settlement.name

        val playerId: UUID = resolveOfflinePlayer(player)
        val slPlayerId = playerId.slPlayerId

        requireIsMemberOf(slPlayerId, settlement)

        fun getWeight(slPlayerId: SLPlayerId) = when {
            SLPlayer.isSettlementLeader(slPlayerId) -> 1001
            else -> SettlementRole.getHighestRole(slPlayerId)?.weight ?: -1
        }

        failIf(getWeight(slPlayerId) >= getWeight(sender.slPlayerId))
        { "$player has a weight greater than or equal to yours, so you can't kick them" }

        SLPlayer.leaveSettlement(slPlayerId)

        Notify.online("&e${sender.name} kicked $player from settlement $settlementName!")
    }

    @Subcommand("set name")
    @Description("Rename your settlement")
    fun onSetName(sender: Player, newName: String, @Optional cost: Int?): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)

        validateName(newName, settlement._id)

        val realCost = NATIONS_BALANCE.settlement.renameCost
        requireMoney(sender, realCost, "rename")

        failIf(cost != realCost) {
            "You must acknowledge the cost of renaming to rename it. " +
                    "The cost is ${realCost.toCreditsString()}. Run the command: " +
                    "/settlement set name $newName $realCost"
        }

        val oldName = getSettlementName(settlement._id)

        Settlement.setName(settlement._id, newName)
        VAULT_ECO.withdrawPlayer(sender, realCost.toDouble())

        Notify.online("&b${sender.name} renamed their settlement $oldName to $newName!")
    }

    @Subcommand("set leader")
    @CommandCompletion("@players")
    @Description("Change the leader of your settlement")
    fun onSetLeader(sender: Player, player: String): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)

        val playerId: UUID = resolveOfflinePlayer(player)
        val slPlayerId = playerId.slPlayerId

        requireIsMemberOf(slPlayerId, settlement)

        Settlement.setLeader(settlement._id, slPlayerId)

        Notify.settlement(settlement._id, "${sender.name} changed your settlement's leader to $player")

        // leader update automatically triggers entire settlement access cache update in CacheHelper
    }

    @Subcommand("set minbuildaccess")
    @CommandCompletion("NONE|ALLY|NATION_MEMBER|SETTLEMENT_MEMBER|STRICT")
    @Description("Change your settlement's minimum build access level")
    fun onSetMinBuildAccess(sender: Player, accessLevel: Settlement.ForeignRelation): Unit = asyncCommand(sender) {
        val settlement = requireSettlementIn(sender)
        requireSettlementLeader(sender, settlement)

        Settlement.setMinBuildAccess(settlement._id, accessLevel)

        Notify.settlement(settlement._id, "${sender.name} changed your settlement's min build access to $accessLevel")
        val description = when (accessLevel) {
            Settlement.ForeignRelation.NONE -> "Anyone, even nationless and settlementless people (should probably NEVER select this)"
            Settlement.ForeignRelation.ALLY -> "Anyone who is a nation ally, nation member, or settlement member"
            Settlement.ForeignRelation.NATION_MEMBER -> "Anyone who is a nation member"
            Settlement.ForeignRelation.SETTLEMENT_MEMBER -> "Anyone who is a settlement member"
            Settlement.ForeignRelation.STRICT -> "No default permission, only people with explicit access from e.g. a role"
        }
        sender msg "&aChanged min build access to $accessLevel. Description: $description"
    }

    @Subcommand("top|list")
    @Description("View the top settlements on Star Legacy")
    fun onTop(sender: CommandSender, @Optional page: Int?): Unit = asyncCommand(sender) {
        val lines = mutableListOf<TextComponent>()
        lines += lineBreak().fromLegacy()

        val settlementIds = SettlementCache.getAll().map { it._id }

        val settlementMembers: Map<Oid<Settlement>, List<SLPlayerId>> =
            settlementIds.associateWith { Settlement.getMembers(it).toList() }

        val lastSeenMap: Map<SLPlayerId, Date> = SLPlayer
            .findProps(SLPlayer::settlementId ne null, SLPlayer::_id, SLPlayer::lastSeen)
            .associate { it[SLPlayer::_id] to it[SLPlayer::lastSeen] }

        val activeMemberCounts: Map<Oid<Settlement>, Int> = settlementMembers.mapValues { (_, members) ->
            members.count { lastSeenMap[it]?.let(::isActive) == true }
        }

        val semiActiveMemberCounts: Map<Oid<Settlement>, Int> = settlementMembers.mapValues { (_, members) ->
            members.count { lastSeenMap[it]?.let(::isSemiActive) == true }
        }

        val sortedSettlements: List<Oid<Settlement>> = settlementIds.toList()
            .sortedByDescending { settlementMembers[it]?.size ?: 0 }
            .sortedByDescending { semiActiveMemberCounts[it] ?: 0 }
            .sortedByDescending { activeMemberCounts[it] ?: 0 }

        val pages = max(1, sortedSettlements.size / super.linesPerPage)
        val index = (max(min(page ?: 1, pages), 1)) - 1
        val settlementsOnPage = sortedSettlements.subList(
            fromIndex = index * linesPerPage,
            toIndex = min(index * linesPerPage + linesPerPage, sortedSettlements.size)
        )

        val nameColor = SLTextStyle.GOLD
        val leaderColor = SLTextStyle.AQUA
        val membersColor = SLTextStyle.BLUE
        val activeColor = SLTextStyle.GREEN
        val semiActiveColor = SLTextStyle.GRAY
        val inactiveColor = SLTextStyle.RED
        val nationColor = SLTextStyle.YELLOW
        val split = "&8|"

        lines += ("${nameColor}Name " +
                "$split ${leaderColor}Leader " +
                "$split ${membersColor}Members &2(${activeColor}Active ${semiActiveColor}Semi-Active ${inactiveColor}Inactive&2) " +
                "$split ${nationColor}Nation").fromLegacy()

        for (settlementId in settlementsOnPage) {
            val settlement = SettlementCache[settlementId]

            val members = settlementMembers[settlementId]!!

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

            val name = settlement.name
            val leaderName = checkNotNull(SLPlayer.getName(settlement.leaderId))

            line.addExtra("    $name ".style(nameColor).cmd("/s info $name").hover("Click for more info"))
            line.addExtra(leaderName.style(leaderColor))
            line.addExtra(" ${members.count()}".style(membersColor))
            line.addExtra(" [".style(SLTextStyle.DARK_GRAY))
            line.addExtra("$active ".style(activeColor))
            line.addExtra("$semiActive ".style(semiActiveColor))
            line.addExtra("$inactive ".style(inactiveColor))
            line.addExtra("]".style(SLTextStyle.DARK_GRAY))

            settlement.nationId?.let { nation: Oid<Nation> ->
                line.addExtra(" ${getNationName(nation)}".style(nationColor))
            }

            lines += line
        }

        val pageLine = TextComponent()

        if (index > 0) {
            pageLine.addExtra(darkGreen(" ["))
            pageLine.addExtra(white("<--").cmd("/settlement top $index").hover("Click to see previous page"))
            pageLine.addExtra(darkGreen("]"))
        }

        pageLine.addExtra(darkAqua(" Page "))
        pageLine.addExtra(gray("${index + 1}/$pages "))

        if (index < pages - 1) {
            pageLine.addExtra(darkGreen(" ["))
            pageLine.addExtra(white("-->").cmd("/settlement top ${index + 2}").hover("Click to see next page"))
            pageLine.addExtra(darkGreen("]"))
        }

        lines += pageLine

        lines += lineBreak().fromLegacy()

        lines.forEach(sender::msg)
    }

    @Subcommand("info")
    @CommandCompletion("@settlements")
    fun onInfo(sender: CommandSender, @Optional settlementName: String?): Unit = asyncCommand(sender) {
        val settlement = when (sender) {
            is Player -> {
                settlementName?.let(::resolveSettlement)
                    ?: PlayerCache[sender].settlementId?.let(SettlementCache::get)
                    ?: fail { "You need to specify a settlement. /s info <settlement>" }
            }
            else -> resolveSettlement(settlementName ?: fail { "Non-players must specify a settlement" })
        }

        val lines = mutableListOf<TextComponent>()
        lines += lineBreak().fromLegacy()

        lines += "                                 &6&b${settlement.name}".fromLegacy()

        settlement.nationId?.let { nationId ->
            val relation: NationRelation.Level = getRelation(sender, NationCache[nationId])

            lines += "&3Nation:&7${relation.textStyle} ${getNationName(nationId)} &8(&7Relation: ${relation.coloredName}&8)".fromLegacy()
        }


        val centerX = settlement.chunks.map { chunkKeyX(it) }.average().roundToInt()
        val centerZ = settlement.chunks.map { chunkKeyZ(it) }.average().roundToInt()
        lines += "&3Location:&7 In world ${settlement.worldName} at $centerX, $centerZ".fromLegacy()

        lines += "&3Area:&7 ${settlement.chunks.size} chunks".fromLegacy()

        lines += "&3Balance:&7 ${settlement.balance}".fromLegacy()

        lines += "&3Leader:&7 ${getSettlementTag(settlement.leaderId, getPlayerName(settlement.leaderId))}".fromLegacy()

        val activeStyle = SLTextStyle.GREEN
        val semiActiveStyle = SLTextStyle.GRAY
        val inactiveStyle = SLTextStyle.RED
        val members: List<Triple<SLPlayerId, String, Date>> = SLPlayer
            .findProps(SLPlayer::settlementId eq settlement._id, SLPlayer::lastKnownName, SLPlayer::lastSeen)
            .map { Triple(it[SLPlayer::_id], it[SLPlayer::lastKnownName], it[SLPlayer::lastSeen]) }
            .sortedByDescending { it.third }

        val names = mutableListOf<String>()
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
            names.add(getSettlementTag(playerId, name, style))
        }
        lines += "&3Members:&7 (${members.size}) &7(&a$active Active &7$semiActive Semi-Active &c$inactive Inactive&7)"
            .fromLegacy()
        val limit = 10
        lines += names.joinToString("&7, &r", limit = limit)
            .replace("${SLTextStyle.RESET}", "&7")
            .fromLegacy()
        if (names.size > limit) {
            lines += darkPurple("[Hover for full member list]")
                .hover(names.joinToString("&7, &r").colorize())
        }

        lines += lineBreak().fromLegacy()

        lines.forEach(sender::msg)
    }

    @Subcommand("zone|region")
    fun onZone(@Suppress("UNUSED_PARAMETER") sender: CommandSender): Unit =
        fail { "Use /szone, not /s zone (remove the space)" }

    @Subcommand("plot")
    fun onPlot(@Suppress("UNUSED_PARAMETER") sender: CommandSender): Unit =
        fail { "Use /splot, not /s plot (remove the space)" }

    @Subcommand("role")
    fun onRole(@Suppress("UNUSED_PARAMETER") sender: CommandSender): Unit =
        fail { "Use /srole, not /s role (remove the space)" }
}
