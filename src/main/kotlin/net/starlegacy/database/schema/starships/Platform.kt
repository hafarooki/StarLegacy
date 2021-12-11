package net.starlegacy.database.schema.starships

import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import org.bukkit.Bukkit
import org.bukkit.World
import org.litote.kmongo.deleteOneById
import org.litote.kmongo.ensureIndex
import org.litote.kmongo.ensureUniqueIndex

/**
 * This can either represent an unpiloted ship, which is stored in database,
 * or a piloted ship, which is only stored in memory.
 *
 * In general this should be cached and only one instance should exist per ship, even if it is piloted and
 * not currently in the database.
 */
data class Platform(
    override val _id: Oid<Platform>,
    var world: String,
    var blockKey: Long,
    var blocks: LongArray,
    val mass: Double,
) : DbObject {
    companion object : OidDbObjectCompanion<Platform>(Platform::class, setup = {
        ensureIndex(Platform::world)
        ensureUniqueIndex(Platform::world, Platform::blockKey)
    }) {
        fun add(data: Platform) {
            col.insertOne(data)
        }

        fun remove(id: Oid<Platform>) {
            col.deleteOneById(id)
        }
    }

    fun bukkitWorld(): World = requireNotNull(Bukkit.getWorld(world)) {
        "World $world is not loaded, but tried getting it for computer $_id"
    }
}
