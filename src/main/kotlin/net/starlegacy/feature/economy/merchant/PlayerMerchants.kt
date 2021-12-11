package net.starlegacy.feature.economy.merchant

import com.github.stefvanschie.inventoryframework.Gui
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.event.NPCRightClickEvent
import net.citizensnpcs.api.npc.MemoryNPCDataStore
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.npc.NPCRegistry
import net.citizensnpcs.trait.LookClose
import net.citizensnpcs.trait.SkinTrait
import net.starlegacy.PLUGIN
import net.starlegacy.SLComponent
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.database.Oid
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.feature.economy.merchant.menus.openBuyMenu
import net.starlegacy.feature.economy.merchant.menus.openSellMenu
import net.starlegacy.feature.nations.gui.*
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Manages NPCs for cities, handles the synchronization of them with the worlds
 */
object PlayerMerchants : SLComponent() {
    private val isCitizensLoaded get() = plugin.server.pluginManager.isPluginEnabled("Citizens")

    private val idMap = mutableMapOf<UUID, Oid<PlayerMerchant>>()

    private lateinit var citizensRegistry: NPCRegistry

    override fun onEnable() {
        if (!isCitizensLoaded) {
            log.warn("Citizens not loaded! No NPCs!")
            return
        } else {
            log.info("Citizens hooked!")
        }

        synchronize()
    }

    override fun onDisable() {
        // Citizens doesn't clear entities properly on reload.
        // Our plugins are reload safe, so we must do it manually.
        clearCitizensNpcs()
    }

    private data class MerchantInfo(
            val merchant: PlayerMerchant,
            val skin: Skins.SkinData,
    )

    /**
     * This methods removes all current tracked Citizens NPCs.
     * Then, it creates new Citizens NPCs for every NPC in the database.
     */
    fun synchronizeAsync(callback: (() -> Unit) = { }) {
        Tasks.async {
            synchronize(callback)
        }
    }

    private fun synchronize(callback: () -> Unit = { }) {
        if (!isCitizensLoaded) {
            return
        }

        val merchantInfo: List<MerchantInfo> = PlayerMerchant.all().mapNotNull { merchant ->
            val id = merchant._id

            val name: String = getDisplayName(merchant)

            val settlementId: Oid<Settlement> = merchant.settlementId
            val settlement = SettlementCache[settlementId]

            val world: World = Bukkit.getWorld(settlement.worldName)
                    ?: return@mapNotNull null

            val x: Double = merchant.x
            val y: Double = merchant.y
            val z: Double = merchant.z
            val location = Location(world, x, y, z)

            val skinDataBytes = Base64.getDecoder().decode(merchant.skinData)
            val skin = Skins.SkinData.fromBytes(skinDataBytes)

            return@mapNotNull MerchantInfo(merchant, skin)
        }

        Tasks.sync {
            idMap.clear()
            clearCitizensNpcs()

            citizensRegistry = CitizensAPI.createNamedNPCRegistry("player-merchant-npcs", MemoryNPCDataStore())

            val spawned = mutableSetOf<Oid<PlayerMerchant>>()

            // create new NPCs and update the ID list
            merchantInfo.forEach { info: MerchantInfo ->
                val worldName = SettlementCache.get(info.merchant.settlementId).worldName
                val location = Location(Bukkit.getWorld(worldName), info.merchant.x, info.merchant.y, info.merchant.z)

                val npc = citizensRegistry.createNPC(EntityType.PLAYER, "${SLTextStyle.GOLD}${info.merchant.name}"


                )
                idMap[npc.uniqueId] = info.merchant._id

                loadChunkAsync(location.world, location) {
                    if (!spawned.add(info.merchant._id)) {
                        log.warn("Spawn task called more than once for city NPC $info")
                        return@loadChunkAsync
                    }

                    spawnNPC(location, npc, info)
                }

                log.debug("Created NPC ${npc.uniqueId} (${npc.name})")
            }

            callback()
        }
    }

    private fun getDisplayName(merchant: PlayerMerchant): String {
        val playerName = SLPlayer.getName(merchant.playerId)
        return "$playerName's Merchant"
    }

    private fun spawnNPC(location: Location, npc: NPC, info: MerchantInfo) {
        check(location.isChunkLoaded)

        npc.getTrait(SkinTrait::class.java).apply {
            setSkinPersistent(info.merchant.name, info.skin.signature, info.skin.value)
        }

        npc.getTrait(LookClose::class.java).apply {
            lookClose(true)
            setRealisticLooking(true)
        }

        npc.isProtected = true

        npc.spawn(location)
    }

    /**
     * Open shipment dialog when players click merchant NPCs.
     */
    @EventHandler
    fun onClickNPC(event: NPCRightClickEvent) {
        val player: Player = event.clicker
        val npc: NPC = event.npc

        val merchantId = idMap[npc.uniqueId] ?: return

        val gui = Gui(PLUGIN, 2, "Merchant")

        // top bar buttons
        gui.addPane(staticPane(0, 0, 9, 1)
                .withItem(guiButton(Material.CHEST) {
                    loadMerchant(merchantId) { openSellMenu(player, it) }
                }.name("Sell Items"), 0, 0)
                .withItem(guiButton(ItemStack(Material.EMERALD_BLOCK, 1)) {
                    loadMerchant(merchantId) { openBuyMenu(player, it) }
                }.name("Buy Items"), 1, 0))

        //list
        gui.addPane(outlinePane(0, 1, 9, 3))

        gui.show(player)
    }

    private fun loadMerchant(merchantId: Oid<PlayerMerchant>, function: (PlayerMerchant) -> Unit) {
        Tasks.async {
            val merchant = PlayerMerchant.findById(merchantId)
                    ?: return@async

            Tasks.sync {
                function(merchant)
            }
        }
    }

    private fun clearCitizensNpcs() {
        if (PlayerMerchants::citizensRegistry.isInitialized) {
            citizensRegistry.toList().forEach { it.destroy() }
            citizensRegistry.deregisterAll()
            CitizensAPI.removeNamedNPCRegistry("player-merchant-npcs")
        }
    }
}
