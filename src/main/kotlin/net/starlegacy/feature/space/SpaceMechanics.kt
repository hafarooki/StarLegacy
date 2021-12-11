package net.starlegacy.feature.space

import net.starlegacy.SLComponent
import net.starlegacy.cache.space.PlanetCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.feature.gear.powerarmor.PowerArmorManager
import net.starlegacy.feature.gear.powerarmor.PowerArmorModule
import net.starlegacy.feature.misc.getPower
import net.starlegacy.feature.misc.removePower
import net.starlegacy.feature.space.WorldFlags.Flag
import net.starlegacy.feature.space.WorldFlags.isFlagSet
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.player.PlayerMoveEvent
import java.util.concurrent.TimeUnit

object SpaceMechanics : SLComponent() {
    override fun onEnable() {
        Tasks.syncRepeat(10, 10) {
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode != GameMode.SURVIVAL || player.isDead || !player.hasGravity()) {
                    continue
                }

                val space = isFlagSet(player.world, Flag.SPACE)

                if (!space) {
                    continue
                }

                if (!space || isInside(player.eyeLocation, 1)) {
                    player.allowFlight = true
                    player.flySpeed = 0.06f
                    continue
                }

                player.allowFlight = true

                @Suppress("DEPRECATION")
                if (!player.isFlying && !player.isOnGround) {
                    player.isFlying = true
                }

                player.flySpeed = 0.02f

                if (player.isSprinting) {
                    player.isSprinting = false
                }

                checkSuffocation(player)
            }
        }

        subscribe<ItemSpawnEvent> { event ->
            val entity = event.entity

            if (!isFlagSet(entity.world, Flag.SPACE)) {
                return@subscribe
            }

            entity.setGravity(false)
            entity.velocity = entity.velocity.multiply(0.05)
        }

        subscribe<PlayerMoveEvent> { event ->
            val player = event.player

            if (!isFlagSet(player.world, Flag.SPACE)) {
                return@subscribe
            }

            val isPositiveChange = event.to.y - event.from.y > 0

            if (event.to.y < -5 && !isPositiveChange || event.to.y > 260 && isPositiveChange) {
                event.isCancelled = true
            }
        }

        subscribe<EntityDamageEvent> { event ->
            if (isFlagSet(event.entity.world, Flag.SPACE) && event.cause == EntityDamageEvent.DamageCause.FALL) {
                event.isCancelled = true
            }
        }

        subscribe<EntityChangeBlockEvent> { event ->
            val entity = event.entity
            if (entity is FallingBlock && isFlagSet(event.block.world, Flag.SPACE)) {
                event.isCancelled = true
                event.block.setBlockData(event.blockData, false)
            }
        }

        subscribe<BlockBreakEvent> { event ->
            val world = event.block.world
            if (!isFlagSet(world, Flag.SPACE)) {
                return@subscribe
            }

            val x = event.block.x.toDouble()
            val y = event.block.y.toDouble()
            val z = event.block.z.toDouble()

            fun check(world: World?, loc: Vec3i, radius: Int): Boolean {
                if (world != world) {
                    return false
                }

                return distanceSquared(
                    x,
                    y,
                    z,
                    loc.x.toDouble(),
                    loc.y.toDouble(),
                    loc.z.toDouble()
                ) <= radius.squared()
            }

            for (star in StarCache.getAll()) {
                if (check(star.instance.spaceWorld, star.instance.position, star.classification.radius)) {
                    event.isCancelled = true
                    return@subscribe
                }
            }

            for (planet in PlanetCache.getAll()) {
                if (check(planet.instance.spaceWorld, planet.instance.position, planet.atmosphereRadius)) {
                    event.isCancelled = true
                    return@subscribe
                }
            }
        }
    }

    private fun checkSuffocation(player: Player) {
        if (isWearingSpaceSuit(player)) {
            return
        }

        if (checkPressureField(player)) {
            return
        }

        player.damage(0.5)
    }

    private fun isWearingSpaceSuit(player: Player): Boolean {
        val inventory = player.inventory
        return inventory.helmet?.type == Material.CHAINMAIL_HELMET &&
                inventory.chestplate?.type == Material.CHAINMAIL_CHESTPLATE &&
                inventory.leggings?.type == Material.CHAINMAIL_LEGGINGS &&
                inventory.boots?.type == Material.CHAINMAIL_BOOTS
    }

    private val pressureFieldPowerCooldown = PerPlayerCooldown(1, TimeUnit.SECONDS)

    private fun checkPressureField(player: Player): Boolean {
        val helmet = player.inventory.helmet
            ?: return false

        if (!PowerArmorManager.hasModule(helmet, PowerArmorModule.PRESSURE_FIELD)) {
            return false
        }

        val powerUsage = 10

        if (getPower(helmet) < powerUsage) {
            return false
        }

        pressureFieldPowerCooldown.tryExec(player) {
            removePower(helmet, powerUsage)
        }
        return true
    }
}
