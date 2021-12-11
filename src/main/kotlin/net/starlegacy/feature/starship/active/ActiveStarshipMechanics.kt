package net.starlegacy.feature.starship.active

import net.starlegacy.SLComponent
import net.starlegacy.feature.starship.DeactivatedPlayerStarships
import net.starlegacy.feature.starship.PilotedStarships
import net.starlegacy.feature.starship.StarshipDestruction
import net.starlegacy.feature.starship.control.StarshipControl
import net.starlegacy.feature.starship.subsystem.weapon.interfaces.AutoWeaponSubsystem
import net.starlegacy.feature.starship.subsystem.weapon.StarshipWeapons
import net.starlegacy.feature.starship.subsystem.weapon.TurretWeaponSubsystem
import net.starlegacy.feature.starship.subsystem.weapon.WeaponSubsystem
import net.starlegacy.util.*
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.dynmap.bukkit.DynmapPlugin
import java.util.LinkedList
import java.util.concurrent.TimeUnit

object ActiveStarshipMechanics : SLComponent() {
    override fun onEnable() {
        scheduleTasks()
    }

    private fun scheduleTasks() {
        Tasks.syncRepeat(20L, 20L, this::deactivateUnpilotedPlayerStarships)
        Tasks.syncRepeat(1L, 1L, this::chargeSubsystems)
        Tasks.syncRepeat(5L, 5L, this::fireAutoWeapons)
        Tasks.syncRepeat(60L, 60L, this::destroyLowHullIntegrityShips)
        Tasks.syncRepeat(20L, 20L, this::tickPlayers)
    }

    private fun deactivateUnpilotedPlayerStarships() {
        for (ship in ActiveStarships.allPlayerShips()) {
            if (ship.pilot == null && ship.minutesUnpiloted >= 5) {
                DeactivatedPlayerStarships.deactivateAsync(ship)
            }
        }
    }

    private fun chargeSubsystems() {
        for (ship in ActiveStarships.all()) {
            val now = System.nanoTime()
            val delta: Double = TimeUnit.NANOSECONDS.toMillis(now - ship.lastTick).toDouble() / 1_000.0
            ship.lastTick = now

            ship.powerSource.tick(delta)
        }
    }

    private fun fireAutoWeapons() {
        for (ship in ActiveStarships.all()) {
            val chunkX = ship.centerOfMass.x shr 4
            val chunkZ = ship.centerOfMass.z shr 4

            if (!ship.world.isChunkLoaded(chunkX, chunkZ)) {
                continue
            }

            val queuedShots = queueShots(ship)
            StarshipWeapons.fireQueuedShots(queuedShots, ship)
        }
    }

    private fun queueShots(ship: ActiveStarship): LinkedList<StarshipWeapons.AutoQueuedShot> {
        val queuedShots = LinkedList<StarshipWeapons.AutoQueuedShot>()

        for (weapon in ship.weapons) {
            val queuedShot = getQueuedShot(ship, weapon)
                ?: continue

            queuedShots.add(queuedShot)
        }

        return queuedShots
    }

    private fun getQueuedShot(
        ship: ActiveStarship,
        weapon: WeaponSubsystem
    ): StarshipWeapons.AutoQueuedShot? {
        if (weapon !is AutoWeaponSubsystem) {
            return null
        }

        if (!weapon.isIntact()) {
            return null
        }

        if (!weapon.isCooledDown()) {
            return null
        }

        val potentialTargets = ship.getPotentialTargets(weapon)

        val world = ship.world

        for (potentialTarget in potentialTargets) {
            if (potentialTarget.world != world) {
                continue
            }

            var targetLoc: Location = potentialTarget.eyeLocation

            val targetRiding = ActiveStarships.findByPassenger(potentialTarget)
            if (targetRiding != null && weapon.shouldTargetRandomBlock(potentialTarget)) {
                targetLoc = Vec3i(targetRiding.blocks.randomEntry()).toLocation(world).toCenterLocation()
            }

            if (weapon is TurretWeaponSubsystem && targetRiding == null) {
                continue
            }

            val targetVec = targetLoc.toVector()
            val direct = targetVec.clone().subtract(ship.centerOfMass.toCenterVector()).normalize()

            if (targetVec.distanceSquared(weapon.pos.toCenterVector()) > weapon.range.squared()) {
                continue
            }

            val dir = weapon.getAdjustedDir(direct, targetVec)

            if (weapon is TurretWeaponSubsystem && !weapon.ensureOriented(dir)) {
                continue
            }

            if (!weapon.canFire(dir, targetVec)) {
                continue
            }

            return StarshipWeapons.AutoQueuedShot(weapon, targetLoc, dir)
        }

        return null
    }

    private fun destroyLowHullIntegrityShips() {
        ActiveStarships.all().filterNot { it is ActivePlatform }.forEach { ship ->
            if (ship.hullIntegrity() < 0.8) {
                StarshipDestruction.destroy(ship)
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (ActiveStarships.findByBlock(event.block) != null) {
            event.isCancelled = true
            event.player actionAndMsg "&cThat block is part of an active starship!"
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val starship = ActiveStarships.findByPassenger(player) ?: return
        val (x, y, z) = Vec3i(event.to)
        if (starship.isWithinHitbox(x, y, z, 2)) {
            return
        }
        event.isCancelled = true
        player msg "&cCan't leave piloted ship. To leave, use /stopriding."
        // a tick later
        Tasks.sync {
            if (!starship.isWithinHitbox(player)) {
                if (PilotedStarships[player] == starship) {
                    PilotedStarships.unpilot(starship)
                    player msg "&cYou got outside of the ship, so it was unpiloted!"
                } else {
                    starship.removePassenger(player.uniqueId)
                    player msg "&cYou got outside of the ship, so you're no longer riding it!"
                }
            }
        }
    }

    private var allowBlockExplosion = false

    fun withBlockExplosionDamageAllowed(block: () -> Unit) {
        try {
            allowBlockExplosion = true
            block()
        } finally {
            allowBlockExplosion = false
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        // block explosion damage has too many problems with starships
        if (event.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return
        }

        if (allowBlockExplosion) {
            return
        }

        event.isCancelled = true
    }

    private fun tickPlayers() {
        for (player in plugin.server.onlinePlayers) {
            val starship = ActiveStarships.findByPilot(player)
            updateDynmapVisibility(player, starship)
            updateGlowing(player, starship)
        }
    }

    private fun updateDynmapVisibility(player: Player, starship: ActivePlayerStarship?) {
        val isNoStarship = starship == null
        val isHoldingController = StarshipControl.isHoldingController(player)
        val isInvisible = isNoStarship && !isHoldingController
        DynmapPlugin.plugin.assertPlayerInvisibility(player, isInvisible, plugin)
    }

    private fun updateGlowing(player: Player, starship: ActivePlayerStarship?) {
        val shouldGlow = starship != null
        if (player.isGlowing != shouldGlow) {
            player.isGlowing = shouldGlow
        }
    }

    override fun onDisable() {
        // release all ships on shutdown
        ActiveStarships.allPlayerShips().forEach { DeactivatedPlayerStarships.deactivateNow(it) }
    }
}
