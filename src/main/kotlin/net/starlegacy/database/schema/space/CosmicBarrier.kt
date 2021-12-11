package net.starlegacy.database.schema.space

import com.fasterxml.jackson.annotation.JsonIgnore
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.ensureUniqueIndexCaseInsensitive
import net.starlegacy.database.generateOid
import net.starlegacy.feature.space.celestialbody.instance.CosmicBarrierInstance
import org.litote.kmongo.set
import org.litote.kmongo.setTo

data class CosmicBarrier(
    override val _id: Oid<CosmicBarrier> = generateOid(),
    override val name: String,
    val spaceWorld: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val radius: Int,
) : CelestialBody() {
    companion object : OidDbObjectCompanion<CosmicBarrier>(CosmicBarrier::class, setup = {
        ensureUniqueIndexCaseInsensitive(CosmicBarrier::name)
    }) {
        fun create(
            name: String,
            spaceWorld: String,
            x: Int,
            y: Int,
            z: Int,
            radius: Int
        ): Oid<CosmicBarrier> {
            val id = generateOid<CosmicBarrier>()
            col.insertOne(CosmicBarrier(id, name, spaceWorld, x, y, z, radius))
            return id
        }

        fun setPos(id: Oid<CosmicBarrier>, spaceWorld: String, x: Int, y: Int, z: Int) {
            updateById(
                id,
                set(
                    CosmicBarrier::spaceWorld setTo spaceWorld,
                    CosmicBarrier::x setTo x,
                    CosmicBarrier::y setTo y,
                    CosmicBarrier::z setTo z
                )
            )
        }
    }

    @delegate:JsonIgnore
    override val instance by lazy { CosmicBarrierInstance(this) }
}
