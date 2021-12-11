package net.starlegacy.database.schema.space

import net.starlegacy.database.*

data class SpaceZone(
    override val _id: Oid<SpaceZone> = generateOid(),
    val name: String,
    val spaceWorld: String,
    val radialCoord: Double,
    val angularCoord: Double
) : DbObject {
    companion object : OidDbObjectCompanion<SpaceZone>(SpaceZone::class, setup = {
        ensureUniqueIndexCaseInsensitive(SpaceZone::name)
    }) {
        fun create(
            name: String,
            spaceWorld: String,
            radialCoord: Double,
            angularCoord: Double
        ): Oid<SpaceZone> {
            val id = generateOid<SpaceZone>()
            col.insertOne(SpaceZone(id, name, spaceWorld, radialCoord, angularCoord))
            return id
        }
    }
}
