package net.starlegacy.feature.space.hazard

import net.starlegacy.SLComponent
import net.starlegacy.cache.space.CosmicBarrierCache
import net.starlegacy.cache.space.StarCache
import net.starlegacy.database.schema.space.CosmicBarrier
import net.starlegacy.database.schema.space.Star
import net.starlegacy.feature.space.celestialbody.instance.CosmicBarrierInstance
import net.starlegacy.feature.starship.StarshipDestruction
import net.starlegacy.feature.starship.active.ActiveStarship
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.util.Tasks
import net.starlegacy.util.d
import net.starlegacy.util.distanceSquared
import net.starlegacy.util.squared
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import kotlin.math.max

object SpaceHazards : SLComponent() {
    private const val INTERVAL_SECONDS = 0.25
    private const val STAR_RANGE = 100.0
    private const val SHIELD_THRESHOLD = 0.01
    private const val PLAYER_DAMAGE = 10 * INTERVAL_SECONDS
    private const val SHIELD_DAMAGE = (10000 * INTERVAL_SECONDS).toInt()

    override fun onEnable() {
        val tickInterval = (20 * INTERVAL_SECONDS).toLong()

        Tasks.syncRepeat(tickInterval, tickInterval) {
            for (starship in ActiveStarships.all()) {
                applyStarHazards(starship)
                applyCosmicBarrierHazards(starship)
            }

            for (player in Bukkit.getOnlinePlayers()) {
                applyStarHazards(player)
                applyCosmicBarrierHazards(player)
            }
        }
    }

    fun applyCosmicBarrierHazards(player: Player) {
        val playerLoc = player.location

        val cosmicBarrier = getNearbyCosmicBarrier(playerLoc) ?: return

        if (isInsideCosmicBarrier(playerLoc, cosmicBarrier)) {
            player.health = 0.0
            return
        }

        if (!shouldHarm(player)) {
            return
        }

        player.damage(PLAYER_DAMAGE)
        player.fireTicks = 1000
    }

    fun applyCosmicBarrierHazards(starship: ActiveStarship) {
        val starshipLoc = starship.centerOfMass.toLocation(starship.world)

        val cosmicBarrier = getNearbyCosmicBarrier(starshipLoc)
            ?: return

        if (isInsideCosmicBarrier(starshipLoc, cosmicBarrier)) {
            StarshipDestruction.destroy(starship)
            return
        }

        if (starship.blockCount <= 0) {
            return
        }

        for (i in 0 until max(1, starship.blockCount / 50)) {
            val block = starship.world.getBlockAtKey(starship.blocks.random())

            absorbShieldImpact(starship, block)

            if (isShielded(starship, block)) {
                continue
            }

            deteriorateBlocks(starship) {
                when (it) {
                    Material.BLUE_ICE -> Material.AIR
                    Material.PACKED_ICE -> Material.BLUE_ICE
                    Material.ICE -> Material.PACKED_ICE
                    else -> Material.ICE
                }
            }
        }
    }

    fun applyStarHazards(player: Player) {
        val playerLoc = player.location

        if (getNearbyStar(playerLoc) == null) {
            return
        }

        if (!shouldHarm(player)) {
            return
        }

        player.damage(PLAYER_DAMAGE)
        player.fireTicks = 1000
    }

    fun applyStarHazards(starship: ActiveStarship) {
        val starshipLoc = starship.centerOfMass.toLocation(starship.world)

        if (getNearbyStar(starshipLoc) == null) {
            return
        }

        if (starship.blockCount <= 0) {
            return
        }

        deteriorateBlocks(starship) {
            if (it != Material.MAGMA_BLOCK) Material.MAGMA_BLOCK
            else Material.AIR
        }
    }

    private fun shouldHarm(player: Player): Boolean {
        if (player.isDead) {
            return false
        }

        val starships = ActiveStarships.findAllContaining(player)
        val block = player.location.block

        if (starships.any { ship -> isShielded(ship, block) }) {
            return false
        }

        return true
    }

    private fun isInsideCosmicBarrier(location: Location, cosmicBarrier: CosmicBarrier): Boolean {
        val distance = cosmicBarrier.instance.location.distance(location)
        val threshold = cosmicBarrier.radius
        return distance < threshold
    }

    private fun deteriorateBlocks(starship: ActiveStarship, getNextState: (Material) -> Material) {
        for (i in 0 until max(1, starship.blockCount / 100)) {
            val block = starship.world.getBlockAtKey(starship.blocks.random())

            absorbShieldImpact(starship, block)

            if (isShielded(starship, block)) {
                continue
            }

            block.type = getNextState(block.type)
        }
    }

    private fun absorbShieldImpact(starship: ActiveStarship, block: Block) {
        val shields = starship.shields.filter { it.containsBlock(block) && it.powerRatio >= SHIELD_THRESHOLD }

        for (shield in shields) {
            shield.power -= SHIELD_DAMAGE
        }
    }

    private fun isShielded(starship: ActiveStarship, block: Block): Boolean {
        return starship.shields.any { it.containsBlock(block) && it.powerRatio >= SHIELD_THRESHOLD }
    }

    private fun getNearbyCosmicBarrier(originLoc: Location): CosmicBarrier? {
        for (cosmicBarrier in CosmicBarrierCache.getAll()) {
            if (cosmicBarrier.instance.spaceWorld != originLoc.world) {
                continue
            }

            val radius = cosmicBarrier.radius + CosmicBarrierInstance.HALF_RING_THICKNESS

            val barrierLoc = cosmicBarrier.instance.position
            val dist = distanceSquared(originLoc.x, 128.0, originLoc.z, barrierLoc.x.d(), 128.0, barrierLoc.z.d())

            if (dist > radius.squared()) {
                continue
            }

            return cosmicBarrier
        }

        return null
    }

    private fun getNearbyStar(originLoc: Location): Star? {
        for (star in StarCache.getAll()) {
            if (star.instance.spaceWorld != originLoc.world) {
                continue
            }

            val radius = star.classification.radius + STAR_RANGE

            val starLoc = star.instance.position
            val dist = distanceSquared(originLoc.x, 128.0, originLoc.z, starLoc.x.d(), 128.0, starLoc.z.d())

            if (dist > radius.squared()) {
                continue
            }

            return star
        }

        return null
    }
}
