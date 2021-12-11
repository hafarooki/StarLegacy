package net.starlegacy

import co.aikar.commands.BukkitCommandCompletionContext
import co.aikar.commands.BukkitCommandExecutionContext
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.PaperCommandManager
import net.starlegacy.cache.Caches
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.cache.nations.PlayerCache
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.cache.space.BlackHoleCache
import net.starlegacy.cache.space.CosmicBarrierCache
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.cache.trade.EcoStationCache
import net.starlegacy.command.SLCommand
import net.starlegacy.command.economy.*
import net.starlegacy.command.misc.*
import net.starlegacy.command.nations.*
import net.starlegacy.command.nations.admin.NationAdminCommand
import net.starlegacy.command.nations.money.NationMoneyCommand
import net.starlegacy.command.nations.money.SettlementMoneyCommand
import net.starlegacy.command.nations.roles.NationRoleCommand
import net.starlegacy.command.nations.roles.SettlementRoleCommand
import net.starlegacy.command.nations.settlementZones.SettlementPlotCommand
import net.starlegacy.command.nations.settlementZones.SettlementZoneCommand
import net.starlegacy.command.space.*
import net.starlegacy.command.starship.*
import net.starlegacy.database.MongoManager
import net.starlegacy.database.schema.economy.*
import net.starlegacy.database.schema.misc.Shuttle
import net.starlegacy.database.schema.space.BlackHole
import net.starlegacy.database.schema.space.CosmicBarrier
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.database.schema.space.Star
import net.starlegacy.database.schema.starships.Blueprint
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.chat.ChannelSelections
import net.starlegacy.feature.chat.ChatChannel
import net.starlegacy.feature.economy.collectors.CollectionMissions
import net.starlegacy.feature.economy.collectors.Collectors
import net.starlegacy.feature.economy.merchant.PlayerMerchants
import net.starlegacy.feature.gas.Gasses
import net.starlegacy.feature.gear.Gear
import net.starlegacy.feature.machine.AreaShields
import net.starlegacy.feature.machine.BaseShields
import net.starlegacy.feature.machine.PowerMachines
import net.starlegacy.feature.machine.Turrets
import net.starlegacy.feature.misc.*
import net.starlegacy.feature.multiblock.Multiblocks
import net.starlegacy.feature.nations.NationsBalancing
import net.starlegacy.feature.nations.NationsMap
import net.starlegacy.feature.nations.NationsMasterTasks
import net.starlegacy.feature.nations.Sieges
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionSettlementZone
import net.starlegacy.feature.space.*
import net.starlegacy.feature.space.celestialbody.visualizer.CelestialBodyVisualizer
import net.starlegacy.feature.space.hazard.SpaceHazards
import net.starlegacy.feature.space_apartments.SpaceApartments
import net.starlegacy.feature.starship.*
import net.starlegacy.feature.starship.active.ActiveStarshipMechanics
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.feature.starship.control.ContactsDisplay
import net.starlegacy.feature.starship.control.StarshipControl
import net.starlegacy.feature.starship.control.StarshipCruising
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.feature.starship.hyperspace.Hyperspace
import net.starlegacy.feature.starship.hyperspace.HyperspaceBeacons
import net.starlegacy.feature.starship.subsystem.shield.StarshipShields
import net.starlegacy.feature.transport.Extractors
import net.starlegacy.feature.transport.TransportConfig
import net.starlegacy.feature.transport.Wires
import net.starlegacy.feature.transport.pipe.Pipes
import net.starlegacy.feature.transport.pipe.filter.Filters
import net.starlegacy.feature.tutorial.TutorialManager
import net.starlegacy.listener.SLEventListener
import net.starlegacy.listener.gear.*
import net.starlegacy.listener.minigame.BedWarsListener
import net.starlegacy.listener.misc.*
import net.starlegacy.listener.nations.FriendlyFireListener
import net.starlegacy.listener.nations.MovementListener
import net.starlegacy.util.*
import net.starlegacy.util.redisaction.RedisActions
import ninja.egg82.events.BukkitEvents
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.litote.kmongo.eq
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.File
import java.util.*

internal val PLUGIN: StarLegacy get() = StarLegacy.PLUGIN
internal lateinit var SETTINGS: Config

class StarLegacy : JavaPlugin() {
    lateinit var redisPool: JedisPool

    init {
        PLUGIN = this
    }

    companion object {
        lateinit var PLUGIN: StarLegacy
        var INITIALIZATION_COMPLETE: Boolean = false
            private set
    }

    fun namespacedKey(key: String) = NamespacedKey(this, key)

    /**
     * Shared folder defined in config for cross-server config files
     */
    val sharedDataFolder by lazy { File(SETTINGS.sharedFolder).apply { mkdirs() } }

    // put the get() so the classes aren't initialized right away
    private val components: List<SLComponent>
        get() = listOf(
            RedisActions,
            AutoRestart,
            Caches,
            Notify,
            Shuttles,

            ChannelSelections,
            ChatChannel.ChannelActions,

            CombatNPCs,

            CryoPods,
            CustomRecipes,
            GameplayTweaks,

            WorldFlags,
            SpaceMap,
            CelestialBodyVisualizer,
            SpaceMechanics,
            SpaceHazards,

            NationsBalancing,
            Regions,
            NationsMap,

            Sieges,

            Multiblocks,
            PowerMachines,
            AreaShields,
            BaseShields,
            Gasses,

            TransportConfig.Companion,
            Extractors,
            Pipes,
            Filters,
            Wires,

            Gear,

            CollectionMissions,
            Collectors,
            PlayerMerchants,

            SpaceApartments,

            DeactivatedPlayerStarships,
            ActiveStarships,
            ActiveStarshipMechanics,
            PilotedStarships,
            StarshipDetection,
            StarshipComputers,
            StarshipControl,
            StarshipShields,
            StarshipCruising,
            ContactsDisplay,
            Hangars,
            Hyperspace,
            HyperspaceBeacons,
            Turrets,
            StarshipFactories,
            TutorialManager,
            Interdiction,
            StarshipDealers,
            Decomposers,
            Platforms,

            DutyModeMonitor
        )

    // put the get() so the classes aren't initialized right away
    private val listeners: List<SLEventListener>
        get() = listOf(
            JoinLeaveListener,
            ChatListener,
            MovementListener,
            FriendlyFireListener,
            ProtectionListener,

            BlockListener,
            EntityListener,
            FurnaceListener,
            InteractListener,
            InventoryListener,

            BlasterListener,
            DetonatorListener,
            DoubleJumpListener,
            PowerArmorListener,
            PowerToolListener,
            SwordListener,

            BedWarsListener
        )

    override fun onEnable() {
        // Hack. Dumb library has a static plugin set based on which plugin loaded it.
        // Set it to this, since the starlegacy-libs plugin is loading it.
        BukkitEvents::class.java.getDeclaredField("plugin").apply { isAccessible = true }.set(null, this)

        SETTINGS = loadConfig(dataFolder, "config")

        // manually call this for MongoManager, as some of the components break if it's not ready on init
        MongoManager.onEnable()

        System.setProperty("https.protocols", "TLSv1.1,TLSv1.2") // java doesn't do https very well by default...

        enableRedis()

        for (component in components) {
            if (SETTINGS.vanilla && !component.supportsVanilla()) {
                continue
            }

            component.onEnable()
            server.pluginManager.registerEvents(component, this)
        }

        registerListeners()

        registerCommands()

        if (isMaster()) {
            // 20 ticks * 60 = 1 minute, 20 ticks * 60 * 60 = 1 hour
            Tasks.asyncRepeat(20 * 60, 20 * 60 * 60) {
                NationsMasterTasks.executeAll()
            }
        }

        INITIALIZATION_COMPLETE = true
    }

    private fun registerListeners() {
        for (listener in listeners) {
            if (SETTINGS.vanilla && !listener.supportsVanilla()) {
                continue
            }

            listener.register()
        }
    }

    private val commands
        get() = listOf(
            GToggleCommand,
            PlayerInfoCommand,
            DyeCommand,
            GlobalGameRuleCommand,

            APCommand,
            ApartmentCommand,
            BatteryCommand,
            CustomItemCommand,
            ListCommand,
            TransportDebugCommand,
            SLTimeConvertCommand,
            ShuttleCommand,

            SettlementCommand,
            NationCommand,
            NationOutpostCommand,
            NationRelationCommand,

            NationAdminCommand,

            NationMoneyCommand,
            SettlementMoneyCommand,

            NationRoleCommand,
            SettlementRoleCommand,

            SettlementPlotCommand,
            SettlementZoneCommand,

            MerchantCommand,

            SiegeCommand,


            PlanetCommand,
            WorldFlagCommand,
            StarCommand,
            BlackHoleCommand,
            CosmicBarrierCommand,


            CollectedItemCommand,
            CollectorCommand,
            EcoStationCommand,

            MiscStarshipCommands,
            BlueprintCommand,
            StarshipDebugCommand,
            TutorialStartStopCommand,
            HyperspaceBeaconCommand,
            StarshipInfoCommand
        )

    private fun registerCommands() {
        val manager = PaperCommandManager(PLUGIN)

        @Suppress("DEPRECATION")
        manager.enableUnstableAPI("help")

        // Add contexts
        manager.commandContexts.run {
            registerContext(CustomItem::class.java) { c: BukkitCommandExecutionContext ->
                val arg = c.popFirstArg()
                return@registerContext CustomItems[arg]
                    ?: throw InvalidCommandArgument("No custom item $arg found!")
            }

            registerContext(RegionSettlementZone::class.java) { c: BukkitCommandExecutionContext ->
                val arg = c.popFirstArg() ?: throw InvalidCommandArgument("Zone is required")
                return@registerContext Regions.getAllOf<RegionSettlementZone>().firstOrNull { it.name == arg }
                    ?: throw InvalidCommandArgument("Zone $arg not found")
            }

            registerContext(Star::class.java) { c: BukkitCommandExecutionContext ->
                StarCache.getByName(c.popFirstArg().toUpperCase())
                    ?: throw InvalidCommandArgument("No such star")
            }

            registerContext(Planet::class.java) { c: BukkitCommandExecutionContext ->
                PlanetCache.getByName(c.popFirstArg().toUpperCase())
                    ?: throw InvalidCommandArgument("No such star")
            }

            registerContext(BlackHole::class.java) { c: BukkitCommandExecutionContext ->
                BlackHoleCache.getByName(c.popFirstArg().toUpperCase())
                    ?: throw InvalidCommandArgument("No such star")
            }

            registerContext(CosmicBarrier::class.java) { c: BukkitCommandExecutionContext ->
                CosmicBarrierCache.getByName(c.popFirstArg().toUpperCase())
                    ?: throw InvalidCommandArgument("No such cosmic barrier")
            }

            registerContext(EcoStation::class.java) { c: BukkitCommandExecutionContext ->
                val name: String = c.popFirstArg()

                return@registerContext EcoStationCache.getByName(name)
                    ?: throw InvalidCommandArgument("Eco station $name not found")
            }
        }

        // Add async tab completions
        @Suppress("RedundantLambdaArrow")
        mapOf<String, (BukkitCommandCompletionContext) -> List<String>>(
            "gamerules" to { _ -> Bukkit.getWorlds().first().gameRules.toList() },
            "world_flags" to { _ -> WorldFlags.Flag.values().map { it.name } },
            "settlements" to { _ -> SettlementCache.getAll().map { it.name } },
            "member_settlements" to { c ->
                val player = c.player ?: throw InvalidCommandArgument("Players only")
                val nation = PlayerCache[player].nationId

                SettlementCache.getAll().filter { nation != null && it.nationId == nation }.map { it.name }
            },
            "nations" to { _ -> NationCache.getAll().map { it.name } },
            "zones" to { c ->
                val player = c.player ?: throw InvalidCommandArgument("Players only")
                val settlement = PlayerCache[player].settlementId

                Regions.getAllOf<RegionSettlementZone>()
                    .filter { settlement != null && it.settlementId == settlement }
                    .map { it.name }
            },
            "plots" to { c ->
                val player = c.player ?: throw InvalidCommandArgument("Players only")
                val slPlayerId = player.slPlayerId

                Regions.getAllOf<RegionSettlementZone>()
                    .filter { it.owner == slPlayerId }
                    .map { it.name }
            },
            "stars" to { _ -> StarCache.getAll().map(Star::name) },
            "planets" to { _ -> PlanetCache.getAll().map(Planet::name) },
            "blackHoles" to { _ -> BlackHoleCache.getAll().map(BlackHole::name) },
            "cosmicBarriers" to { _ -> CosmicBarrierCache.getAll().map(CosmicBarrier::name) },
            "materials" to { _ -> MATERIALS.map { it.name } },
            "collecteditems" to { _ ->
                CollectedItem.all().map { "${EcoStationCache[it.station].name}.${it.itemString}" }
            },
            "ecostations" to { _ -> EcoStationCache.getAll().map { it.name } },
            "shuttles" to { _ -> Shuttle.all().map { it.name } },
            "shuttleSchematics" to { _ -> Shuttles.getAllSchematics() },
            "blueprints" to { c ->
                val player = c.player ?: throw InvalidCommandArgument("Players only")
                val slPlayerId = player.slPlayerId
                Blueprint.col.find(Blueprint::owner eq slPlayerId).map { it.name }.toList()
            }
        ).forEach { manager.commandCompletions.registerAsyncCompletion(it.key, it.value) }

        // Register commands
        for (command in commands) {
            if (SETTINGS.vanilla && !command.supportsVanilla()) {
                continue
            }

            manager.registerCommand(command)
        }
    }

    private fun enableRedis() {
        redisPool = JedisPool(JedisPoolConfig(), SETTINGS.redis.host)
    }

    override fun onDisable() {
        SLCommand.ASYNC_COMMAND_THREAD.shutdown()

        for (component in components.asReversed()) {
            if (SETTINGS.vanilla && !component.supportsVanilla()) {
                continue
            }

            try {
                component.onDisable()
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
        }
        redisPool.close()

        // manually disable here since it's not a listed component for the same reason it's manual onEnable
        MongoManager.onDisable()
    }

    inline fun <reified T : Event> listen(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline block: (T) -> Unit
    ): Unit = listen<T>(priority, ignoreCancelled) { _, event -> block(event) }

    inline fun <reified T : Event> listen(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline block: (Listener, T) -> Unit
    ) {
        server.pluginManager.registerEvent(
            T::class.java,
            object : Listener {},
            priority,
            { listener, event -> block(listener, event as? T ?: return@registerEvent) },
            this,
            ignoreCancelled
        )
    }

    fun isMaster(): Boolean = SETTINGS.master
}

fun <T> redis(block: Jedis.() -> T): T = PLUGIN.redisPool.resource.use(block)
