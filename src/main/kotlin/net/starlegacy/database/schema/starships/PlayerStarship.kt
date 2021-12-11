package net.starlegacy.database.schema.starships

import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.starship.StarshipType
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.litote.kmongo.contains
import org.litote.kmongo.deleteOneById
import org.litote.kmongo.ensureIndex
import org.litote.kmongo.ensureUniqueIndex
import org.litote.kmongo.eq
import org.litote.kmongo.or

/**
 * This can either represent an unpiloted ship, which is stored in database,
 * or a piloted ship, which is only stored in memory.
 *
 * In general this should be cached and only one instance should exist per ship, even if it is piloted and
 * not currently in the database.
 */
data class PlayerStarship(
    override val _id: Oid<PlayerStarship>,
    /** Player UUID of the captain of the ship */
    var captain: SLPlayerId,
    var type: StarshipType,

    var world: String,
    var blockKey: Long,

    /** UUIDs of players who have been added to the ship by the captain. Should never include the captain. */
    val pilots: MutableSet<SLPlayerId> = mutableSetOf(),
    var name: String? = null,
    /** Chunk combined coordinates, of each chunk the detected blocks reside in */
    var containedChunks: Set<Long>? = null,

    var lastUsed: Long = System.currentTimeMillis(),
    var isLockEnabled: Boolean = false
) : DbObject {
    companion object : OidDbObjectCompanion<PlayerStarship>(PlayerStarship::class, setup = {
        ensureIndex(PlayerStarship::captain)
        ensureIndex(PlayerStarship::pilots)
        ensureIndex(PlayerStarship::name)
        ensureIndex(PlayerStarship::world)
        ensureUniqueIndex(PlayerStarship::world, PlayerStarship::blockKey)
    }) {
        const val LOCK_TIME_MS = 1_000 * 5;

        fun add(data: PlayerStarship) {
            col.insertOne(data)
        }

        fun remove(id: Oid<PlayerStarship>) {
            col.deleteOneById(id)
        }

        fun findByPilot(playerId: SLPlayerId) =
            find(or(PlayerStarship::captain eq playerId, PlayerStarship::pilots contains playerId))
    }

    fun bukkitWorld(): World = requireNotNull(Bukkit.getWorld(world)) {
        "World $world is not loaded, but tried getting it for computer $_id"
    }

    fun isPilot(player: Player): Boolean {
        val id = player.slPlayerId
        return captain == id || pilots.contains(id)
    }

    /** assumes that it's also deactivated */
    fun isLockActive(): Boolean {
        return isLockEnabled && System.currentTimeMillis() - lastUsed >= LOCK_TIME_MS
    }
}
