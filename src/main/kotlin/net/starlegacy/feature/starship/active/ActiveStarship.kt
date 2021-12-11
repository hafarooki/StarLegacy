package net.starlegacy.feature.starship.active

import com.destroystokyo.paper.Title
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.starlegacy.PLUGIN
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.feature.multiblock.gravitywell.GravityWellMultiblock
import net.starlegacy.feature.starship.StarshipType
import net.starlegacy.feature.starship.movement.StarshipMovement
import net.starlegacy.feature.starship.subsystem.*
import net.starlegacy.feature.starship.subsystem.reactor.PowerSourceSubsystem
import net.starlegacy.feature.starship.subsystem.shield.ShieldSubsystem
import net.starlegacy.feature.starship.subsystem.thruster.ThrustData
import net.starlegacy.feature.starship.subsystem.thruster.ThrusterSubsystem
import net.starlegacy.feature.starship.subsystem.weapon.TurretWeaponSubsystem
import net.starlegacy.feature.starship.subsystem.weapon.WeaponSubsystem
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.NumberConversions
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.asSequence
import kotlin.collections.count
import kotlin.collections.filter
import kotlin.collections.filterIsInstance
import kotlin.collections.forEach
import kotlin.collections.getValue
import kotlin.collections.iterator
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.mutableMapOf
import kotlin.collections.none
import kotlin.collections.set
import kotlin.collections.sumBy
import kotlin.collections.sumByDouble
import kotlin.math.*

abstract class ActiveStarship(
    world: World,
    var blocks: LongOpenHashSet,
    val mass: Double,
    var centerOfMass: Vec3i,
    private val hitbox: ActiveStarshipHitbox
) {
    abstract val type: StarshipType

    var world: World = world
        set(value) {
            ActiveStarships.updateWorld(this, field, value)
            field = value
        }

    var isTeleporting: Boolean = false

    val blockCount: Int = blocks.size

    val subsystems = LinkedList<StarshipSubsystem>()
    lateinit var powerSource: PowerSourceSubsystem
    val shields = LinkedList<ShieldSubsystem>()
    val weapons = LinkedList<WeaponSubsystem>()
    val turrets = LinkedList<TurretWeaponSubsystem>()
    val hyperdrives = LinkedList<HyperdriveSubsystem>()
    val navComps = LinkedList<NavCompSubsystem>()
    val thrusters = LinkedList<ThrusterSubsystem>()
    val magazines = LinkedList<MagazineSubsystem>()
    val gravityWells = LinkedList<GravityWellSubsystem>()

    val weaponSets: HashMultimap<String, WeaponSubsystem> = HashMultimap.create()
    val weaponSetSelections: HashBiMap<UUID, String> = HashBiMap.create()

    val shieldEfficiency: Double
        get() = (shields.size.d().pow(0.9) / (blockCount / 500.0).coerceAtLeast(1.0).pow(0.7))
            .coerceAtMost(1.0)

    val thrusterMap = mutableMapOf<BlockFace, ThrustData>()

    var lastTick = System.nanoTime()

    var isInterdicting = false
        private set

    var interdictionWarmupTask: BukkitRunnable? = null


    fun toggleInterdiction() {
        if (this.isInterdicting) {
            this.setIsInterdicting(false)
            return
        }

        var warmupTask = this.interdictionWarmupTask
        if (warmupTask != null) {
            warmupTask.cancel()
            this.interdictionWarmupTask = null
            sendMessage("&cGravity well warmup cancelled")
            return
        }

        warmupTask = Tasks.bukkitRunnable {
            if (!ActiveStarships.isActive(this@ActiveStarship)) {
                return@bukkitRunnable
            }

            this@ActiveStarship.interdictionWarmupTask = null
            this@ActiveStarship.setIsInterdicting(true)
        }
        warmupTask.runTaskLater(PLUGIN, 10L * 20L)
        this.interdictionWarmupTask = warmupTask
    }

    fun setIsInterdicting(newIsInterdicting: Boolean) {
        Tasks.checkMainThread()

        this.isInterdicting = newIsInterdicting

        // cancel warmup if overriding value irrespective of new value
        val warmupTask = this.interdictionWarmupTask

        if (warmupTask != null) {
            warmupTask.cancel()
            this.interdictionWarmupTask = null
        }

        gravityWells
            .filter { it.isIntact() }
            .map { it.pos.toLocation(world).block.state }
            .filterIsInstance<Sign>()
            .forEach { GravityWellMultiblock.setEnabled(it, newIsInterdicting) }

        if (!newIsInterdicting) {
            sendMessage("&eGravity well disabled")
            return
        }

        sendMessage("&6Gravity well enabled")
    }

    abstract val interdictionRange: Int

    abstract val weaponColor: Color

    var forward: BlockFace = BlockFace.NORTH
    var isExploding = false

    val min: Vec3i get() = hitbox.min
    val max: Vec3i get() = hitbox.max

    inline fun iterateBlocks(x: (Int, Int, Int) -> Unit) {
        for (key in blocks.iterator()) {
            x(blockKeyX(key), blockKeyY(key), blockKeyZ(key))
        }
    }

    fun generateThrusterMap() {
        for (face in CARDINAL_BLOCK_FACES) {
            val faceThrusters = thrusters.filter { it.face == face }
            val data = buildThrustData(faceThrusters)
            thrusterMap[face] = data
        }
    }

    private fun buildThrustData(faceThrusters: List<ThrusterSubsystem>): ThrustData {
        if (faceThrusters.none()) {
            return ThrustData(0.0, 0)
        }

        val baseSpeedFactor = 50.0
        val speedExponent = 0.5
        val massExponent = 0.2
        val reductionBase = 0.85
        val finalSpeedFactor = 1.0

        val mass = this.mass
        val totalAccel = 1.0 + faceThrusters.sumByDouble { it.type.accel }
        val totalWeight = faceThrusters.sumBy { it.type.weight }.toDouble()
        val reduction = reductionBase.pow(sqrt(totalWeight))
        val totalSpeed = faceThrusters.sumByDouble { it.type.speed } * reduction

        val calculatedSpeed = totalSpeed.pow(speedExponent) / mass.pow(massExponent) * baseSpeedFactor

        val maxSpeed = powerSource.output * .4 / totalSpeed

        val speed = (min(maxSpeed, calculatedSpeed) * finalSpeedFactor).roundToInt()

        val acceleration = ln(2.0 + totalAccel) * ln(2.0 + totalWeight) / ln(mass.squared()) * reduction * 30.0
        return ThrustData(acceleration, speed)
    }

    fun calculateHitbox() {
        this.hitbox.calculate(this.blocks)
    }

    fun calculateMinMax() {
        this.hitbox.calculateMinMax(this.blocks)
    }

    fun isInBounds(x: Int, y: Int, z: Int): Boolean {
        return x >= min.x && y >= min.y && z >= min.z && x <= max.x && y <= max.y && z <= max.z
    }

    fun isWithinHitbox(x: Int, y: Int, z: Int, tolerance: Int = 2): Boolean {
        return isInBounds(x, y, z) && hitbox.contains(x, y, z, min.x, min.y, min.z, tolerance)
    }

    fun isWithinHitbox(loc: Location, tolerance: Int = 2): Boolean {
        return world == loc.world && isWithinHitbox(loc.blockX, loc.blockY, loc.blockZ, tolerance)
    }

    fun isWithinHitbox(entity: Entity, tolerance: Int = 2): Boolean {
        val loc = entity.location
        return isWithinHitbox(loc, tolerance)
    }

    fun contains(x: Int, y: Int, z: Int): Boolean {
        return isInBounds(x, y, z) && blocks.contains(blockKey(x, y, z))
    }

    fun isInternallyObstructed(origin: Vec3i, dir: Vector, maxDistance: Int? = null): Boolean {
        var x = origin.x.toDouble() + 0.5
        var y = origin.y.toDouble() + 0.5
        var z = origin.z.toDouble() + 0.5
        var distance = 0
        while (maxDistance == null || distance <= maxDistance) {
            val blockX = NumberConversions.floor(x)
            val blockY = NumberConversions.floor(y)
            val blockZ = NumberConversions.floor(z)
            if (!isInBounds(
                    blockX,
                    blockY,
                    blockZ
                ) && distance > (max.x - min.x) && distance > (max.y - min.y) && distance > (max.z - min.z)
            ) {
                break
            }

            if (contains(blockX, blockY, blockZ)) {
                return true
            }
            x += dir.x
            y += dir.y
            z += dir.z
            distance++
        }
        return false
    }

    private val passengers = HashSet<UUID>()

    val onlinePassengers get() = passengers.mapNotNull(Bukkit::getPlayer)

    fun isPassenger(playerID: UUID): Boolean {
        return passengers.contains(playerID)
    }

    open fun addPassenger(playerID: UUID) {
        passengers.add(playerID)
    }

    open fun removePassenger(playerID: UUID) {
        passengers.remove(playerID)
    }

    open fun clearPassengers() {
        passengers.clear()
    }

    abstract fun moveAsync(movement: StarshipMovement): CompletableFuture<Boolean>

    fun sendActionBar(message: String) {
        for (player in onlinePassengers) {
            player action message
        }
    }

    fun sendTitle(title: Title) {
        onlinePassengers.asSequence().forEach { it title title }
    }

    fun sendMessage(message: String) {
        onlinePassengers.asSequence().forEach { it msg message }
    }

    /** get the thruster data for this direction. if it's diagonal, it returns the faster side's speed. */
    fun getThrustData(dx: Int, dz: Int): ThrustData {
        val xDirection = if (dx > 0) BlockFace.EAST else BlockFace.WEST
        val zDirection = if (dz > 0) BlockFace.SOUTH else BlockFace.NORTH
        val xData = thrusterMap.getValue(xDirection)
        val zData = thrusterMap.getValue(zDirection)
        return when {
            dx != 0 && dz != 0 -> when {
                xData.maxSpeed > zData.maxSpeed -> xData
                zData.maxSpeed > xData.maxSpeed -> zData
                xData.accel > zData.accel -> xData
                zData.accel > xData.accel -> zData
                else -> xData
            }
            dx != 0 -> xData
            dz != 0 -> zData
            else -> error("Can't get thruster data for $dx $dz")
        }
    }

    fun updatePower(sender: Player, shield: Int, weapon: Int, thruster: Int) {
        powerSource.powerDistributor.setDivision(shield / 100.0, weapon / 100.0, thruster / 100.0)
        val name = sender.name
        sendMessage("&a$name&3 updated the power mode to &b$shield% shield &c$weapon% weapon &e$thruster% thruster")
    }

    fun hullIntegrity(): Double {
        val nonAirBlocks = blocks.count {
            getBlockTypeSafe(world, blockKeyX(it), blockKeyY(it), blockKeyZ(it))?.isAir != true
        }
        return nonAirBlocks.toDouble() / blockCount.toDouble()
    }

    fun getEntryRange(planet: Planet): Int {
        return planet.atmosphereRadius + max(max.x - min.x, max.z - min.z) / 2 + 10
    }

    abstract fun getPotentialTargets(weapon: WeaponSubsystem): List<Player>
}
